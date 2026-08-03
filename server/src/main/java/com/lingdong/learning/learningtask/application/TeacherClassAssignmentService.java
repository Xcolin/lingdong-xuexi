package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
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
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final String TEACHER_ROLE = "TEACHER";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";

    private final TeacherClassMapper teacherClassMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;

    public TeacherClassAssignmentService(
            TeacherClassMapper teacherClassMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator
    ) {
        this.teacherClassMapper = teacherClassMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.organizationMapper = organizationMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
    }

    /** 建立或恢复教师班级关系；锁定教师用户行，避免并发首次绑定。 */
    @Transactional
    public TeacherClassRelation assign(AuthenticatedUser currentUser, Long teacherUserId, Long classId) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Long normalizedTeacherId = requiredId(teacherUserId, "教师用户标识");
        Long normalizedClassId = requiredId(classId, "班级组织标识");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Organization classOrganization = requireManageableClass(currentUser, normalizedClassId);

        User teacher = userMapper.findByIdForUpdate(normalizedTeacherId);
        if (teacher == null) {
            throw notFound();
        }
        if (teacher.status() != UserStatus.ENABLED
                || !userRoleMapper.hasRoleCode(normalizedTeacherId, TEACHER_ROLE)) {
            throw new IllegalArgumentException("目标用户不是启用教师");
        }
        if (!teacherClassMapper.existsTeacherOrganizationInClassAncestors(
                normalizedTeacherId, classOrganization.id())) {
            throw notFound();
        }

        TeacherClassRelation existing = teacherClassMapper.findByTeacherAndClass(
                normalizedTeacherId, normalizedClassId);
        if (existing == null) {
            teacherClassMapper.insert(TeacherClassRelation.active(
                    idGenerator.nextId(), normalizedTeacherId, normalizedClassId));
        } else if (existing.status() != TeacherClassStatus.ACTIVE) {
            teacherClassMapper.activate(normalizedTeacherId, normalizedClassId);
        }
        return teacherClassMapper.findByTeacherAndClass(normalizedTeacherId, normalizedClassId);
    }

    /** 解除关系但保留历史记录。 */
    @Transactional
    public void remove(AuthenticatedUser currentUser, Long teacherUserId, Long classId) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Long normalizedTeacherId = requiredId(teacherUserId, "教师用户标识");
        Long normalizedClassId = requiredId(classId, "班级组织标识");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireManageableClass(currentUser, normalizedClassId);
        TeacherClassRelation existing = teacherClassMapper.findByTeacherAndClass(
                normalizedTeacherId, normalizedClassId);
        if (existing == null) {
            throw notFound();
        }
        teacherClassMapper.deactivate(normalizedTeacherId, normalizedClassId);
    }

    /** 教师读取本人班级，机构管理员读取其授权范围内的目标教师班级。 */
    public List<TeacherClassRelation> list(AuthenticatedUser currentUser, Long teacherUserId) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Long normalizedTeacherId = requiredId(teacherUserId, "教师用户标识");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        User teacher = userMapper.findById(normalizedTeacherId);
        if (teacher == null || !userRoleMapper.hasRoleCode(normalizedTeacherId, TEACHER_ROLE)) {
            throw notFound();
        }
        List<TeacherClassRelation> relations = teacherClassMapper.findActiveByTeacher(normalizedTeacherId);
        if (currentUser.userId().equals(normalizedTeacherId)
                && currentUser.roleCodes().contains(TEACHER_ROLE)) {
            return relations;
        }
        if (!currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)) {
            throw new SystemOperationAccessDeniedException("仅教师本人或机构管理员可查询教师班级");
        }
        return relations.stream()
                .filter(relation -> organizationDataScopeService.canAccess(
                        currentUser.userId(), relation.classOrganizationId()))
                .toList();
    }

    private Organization requireManageableClass(AuthenticatedUser currentUser, Long classId) {
        Organization organization = organizationMapper.findById(classId);
        if (organization == null || !organizationDataScopeService.canAccess(currentUser.userId(), classId)) {
            throw notFound();
        }
        if (!"CLASS".equals(organization.typeCode())) {
            throw new IllegalArgumentException("目标组织不是班级");
        }
        if (organization.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("目标班级已停用");
        }
        return organization;
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
