package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.domain.TeacherClassChangeEvent;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** 维护机构授权范围内的教师班级关系。 */
@Service
public class TeacherClassAssignmentService {
    /** 一次提交增删差量，任一关系校验失败时整批回滚；旧单条接口保留。 */
    @Transactional
    public void changeClasses(AuthenticatedUser actor, Long teacherUserId,
                              List<Long> additions, List<Long> removals) {
        if (additions == null || removals == null || additions.size() + removals.size() > 100
                || additions.stream().anyMatch(Objects::isNull) || removals.stream().anyMatch(Objects::isNull)
                || additions.stream().anyMatch(removals::contains)) {
            throw new IllegalArgumentException("班级变更参数不合法");
        }
        if (additions.isEmpty() && removals.isEmpty()) return;
        for (Long id : additions.stream().distinct().toList()) assign(actor, teacherUserId, id);
        for (Long id : removals.stream().distinct().toList()) remove(actor, teacherUserId, id);
    }
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.permission.application.PermissionDecisionService actionPermissions;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.teacher.application.TeacherIdentityService teacherIdentity;
    private static final String LEARNING_TASK_FEATURE = "LEARNING_TASK_MANAGEMENT";
    private static final String TEACHER_MANAGEMENT_FEATURE = "TEACHER_MANAGEMENT";
    private static final String CLASS_MANAGEMENT_FEATURE = "CLASS_MANAGEMENT";
    private static final String TEACHER_ROLE = "TEACHER";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";

    @org.springframework.beans.factory.annotation.Autowired
    private com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper userOrganizationMapper;
    private final TeacherClassMapper teacherClassMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final TeacherReviewAutoTransferService reviewAutoTransferService;
    private final LearningTaskScopeService permissionScope;

    public TeacherClassAssignmentService(
            TeacherClassMapper teacherClassMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            TeacherReviewAutoTransferService reviewAutoTransferService,
            LearningTaskScopeService permissionScope
    ) {
        this.teacherClassMapper = teacherClassMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.organizationMapper = organizationMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.reviewAutoTransferService = reviewAutoTransferService;
        this.permissionScope = permissionScope;
    }

    /** 建立或恢复教师班级关系；锁定教师用户行，避免并发首次绑定。 */
    @Transactional
    public TeacherClassRelation assign(AuthenticatedUser currentUser, Long teacherUserId, Long classId) {
        return assignInternal(currentUser,teacherUserId,classId,"TEACHER_CLASS_ASSIGN");
    }
    /** Initial placement is an atomic user-creation side effect; direct assignment remains protected separately. */
    @Transactional
    public TeacherClassRelation assignCreatedTeacher(AuthenticatedUser currentUser, Long teacherUserId, Long classId) {
        if(currentUser==null||currentUser.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.WEB)throw new SystemOperationAccessDeniedException("用户创建需要Web会话");
        return assignInternal(currentUser,teacherUserId,classId,"IAM_USER_CREATE");
    }
    private TeacherClassRelation assignInternal(AuthenticatedUser currentUser, Long teacherUserId, Long classId,String permission) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Long normalizedTeacherId = requiredId(teacherUserId, "教师用户标识");
        Long normalizedClassId = requiredId(classId, "班级组织标识");
        if(!"IAM_USER_CREATE".equals(permission))requireManagementFeatures();
        Organization classOrganization = requireManageableClass(currentUser, normalizedClassId,permission);

        User teacher = userMapper.findByIdForUpdate(normalizedTeacherId);
        if (teacher == null) {
            throw notFound();
        }
        if (teacher.status() != UserStatus.ENABLED
                || !teacherIdentity.isTeacher(normalizedTeacherId)) {
            throw new IllegalArgumentException("目标用户不是启用教师");
        }
        if (!teacherClassMapper.existsTeacherOrganizationInClassAncestors(
                normalizedTeacherId, classOrganization.id())) {
            throw notFound();
        }

        requireTeacherSourceScope(currentUser, normalizedTeacherId);
        TeacherClassRelation existing = teacherClassMapper.findByTeacherAndClass(
                normalizedTeacherId, normalizedClassId);
        boolean changed = false;
        if (existing == null) {
            changed = teacherClassMapper.insert(TeacherClassRelation.active(
                    idGenerator.nextId(), normalizedTeacherId, normalizedClassId)) == 1;
        } else if (existing.status() != TeacherClassStatus.ACTIVE) {
            changed = teacherClassMapper.activate(normalizedTeacherId, normalizedClassId) == 1;
        }
        if (changed) {
            recordChange(currentUser.userId(), normalizedTeacherId, normalizedClassId,
                    TeacherClassChangeEvent.BIND);
        }
        return teacherClassMapper.findByTeacherAndClass(normalizedTeacherId, normalizedClassId);
    }

    /** 解除关系但保留历史记录。 */
    @Transactional
    public void remove(AuthenticatedUser currentUser, Long teacherUserId, Long classId) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Long normalizedTeacherId = requiredId(teacherUserId, "教师用户标识");
        Long normalizedClassId = requiredId(classId, "班级组织标识");
        requireManagementFeatures();
        Organization classOrganization = requireManageableClass(currentUser, normalizedClassId);
        User teacher = userMapper.findByIdForUpdate(normalizedTeacherId);
        if (teacher == null
                || !teacherIdentity.hasProfile(normalizedTeacherId)
                || !teacherClassMapper.existsTeacherOrganizationInClassAncestors(
                normalizedTeacherId, classOrganization.id())) {
            throw notFound();
        }
        requireTeacherSourceScope(currentUser, normalizedTeacherId);
        TeacherClassRelation existing = teacherClassMapper.findByTeacherAndClass(
                normalizedTeacherId, normalizedClassId);
        if (existing == null) {
            throw notFound();
        }
        if (existing.status() == TeacherClassStatus.ACTIVE) {
            reviewAutoTransferService.transferForClass(
                    currentUser, normalizedTeacherId, normalizedClassId);
            if (teacherClassMapper.deactivate(normalizedTeacherId, normalizedClassId) == 1) {
                recordChange(currentUser.userId(), normalizedTeacherId, normalizedClassId,
                        TeacherClassChangeEvent.UNBIND);
            }
        }
    }

    /** 教师读取本人班级，机构管理员读取其授权范围内的目标教师班级。 */
    public List<TeacherClassRelation> list(AuthenticatedUser currentUser, Long teacherUserId) {return list(currentUser,teacherUserId,"LEARNING_TASK_CREATE");}
    public List<TeacherClassRelation> list(AuthenticatedUser currentUser, Long teacherUserId,String permission) {
        if(!java.util.Set.of("LEARNING_TASK_CREATE","TEACHER_CLASS_ASSIGN").contains(permission))throw new SystemOperationAccessDeniedException("不支持该教师班级选择动作");
        requireActionPermission(currentUser,permission);
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Long normalizedTeacherId = requiredId(teacherUserId, "教师用户标识");
        if("LEARNING_TASK_CREATE".equals(permission))featureAccessService.requireEnabled(LEARNING_TASK_FEATURE, null);
        else requireManagementFeatures();
        User teacher = userMapper.findById(normalizedTeacherId);
        if (teacher == null || !teacherIdentity.isTeacher(normalizedTeacherId)) {
            throw notFound();
        }
        List<TeacherClassRelation> relations = teacherClassMapper.findActiveByTeacher(normalizedTeacherId);
        if (currentUser.userId().equals(normalizedTeacherId)
                && teacherIdentity.isTeacher(currentUser.userId())) {
            return relations;
        }
        return relations.stream()
                .filter(relation -> organizationDataScopeService.canAccess(
                        currentUser.userId(), relation.classOrganizationId()))
                .toList();
    }

    private Organization requireManageableClass(AuthenticatedUser currentUser, Long classId) {return requireManageableClass(currentUser,classId,"TEACHER_CLASS_ASSIGN");}
    private Organization requireManageableClass(AuthenticatedUser currentUser, Long classId,String permission) {
        requireActionPermission(currentUser,permission);
        Organization organization = organizationMapper.findById(classId);
        if (organization == null || !organizationDataScopeService.canAccess(currentUser.userId(), classId)) {
            throw notFound();
        }
        if (!"CLASS".equals(organization.typeCode())) {
            throw new IllegalArgumentException("目标组织不是班级");
        }
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
            throw new IllegalStateException("目标班级已停用");
        }
        return organization;
    }

    private void requireActionPermission(AuthenticatedUser user,String permission){
        if(user==null||user.clientType()==null||!actionPermissions.isAllowed(user.userId(),com.lingdong.learning.permission.domain.PermissionClient.valueOf(user.clientType().name()),permission))
            throw new SystemOperationAccessDeniedException("缺少对应教师班级操作权限");
    }

    private void requireTeacherSourceScope(AuthenticatedUser currentUser, Long teacherId) {
        var scope = organizationDataScopeService.resolve(currentUser.userId());
        boolean visible = userOrganizationMapper.findOrganizationIds(teacherId).stream()
                .map(organizationMapper::findById).filter(Objects::nonNull)
                .filter(OrganizationOperationalStatusService::isOperational).anyMatch(scope::allows);
        if (!visible) throw notFound();
    }

    private void requireManagementFeatures() {
        featureAccessService.requireEnabled(TEACHER_MANAGEMENT_FEATURE, null);
        featureAccessService.requireEnabled(CLASS_MANAGEMENT_FEATURE, null);
    }

    private void recordChange(
            Long operatorUserId,
            Long teacherUserId,
            Long classOrganizationId,
            TeacherClassChangeEvent eventType
    ) {
        if (teacherClassMapper.insertChangeLog(
                idGenerator.nextId(), teacherUserId, classOrganizationId, eventType, operatorUserId) != 1) {
            throw new IllegalStateException("教师班级变更日志保存失败");
        }
    }

    private Long requiredId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("教师或班级不存在或不可访问");
    }
}
