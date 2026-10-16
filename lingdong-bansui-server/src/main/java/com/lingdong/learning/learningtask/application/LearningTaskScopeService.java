package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import com.lingdong.learning.learningtask.domain.TeacherClassRelation;
import com.lingdong.learning.learningtask.domain.TeacherClassStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 统一约束任务来源、原始目标、审核人与操作者的数据范围。 */
@Service
public class LearningTaskScopeService {
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTargetMapper selectedTaskTargets;
    private final com.lingdong.learning.auth.application.ParentActorAccessService parentActors;
    private final com.lingdong.learning.teacher.application.TeacherIdentityService teacherIdentity;
    private final OrganizationMapper organizationMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;
    private final TeacherClassMapper teacherClassMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final com.lingdong.learning.permission.application.PermissionDecisionService permissions;

    public LearningTaskScopeService(
            OrganizationMapper organizationMapper,
            OrganizationDataScopeService organizationDataScopeService,
            ParentStudentMapper parentStudentMapper,
            StudentOrganizationMapper studentOrganizationMapper,
            TeacherClassMapper teacherClassMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            com.lingdong.learning.permission.application.PermissionDecisionService permissions,
            com.lingdong.learning.auth.application.ParentActorAccessService parentActors,
            com.lingdong.learning.teacher.application.TeacherIdentityService teacherIdentity
    ) {
        this.organizationMapper = organizationMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.parentStudentMapper = parentStudentMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
        this.teacherClassMapper = teacherClassMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.assignmentMapper = assignmentMapper;
        this.permissions = permissions;
        this.parentActors = parentActors;
        this.teacherIdentity = teacherIdentity;
    }

    public Long validateAndResolveReviewer(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            Long sourceOrganizationId,
            Long requestedReviewerUserId,
            ValidatedLearningTaskDraft draft
    ) {
        Objects.requireNonNull(sourceType, "任务来源不能为空");
        Objects.requireNonNull(draft, "任务草稿不能为空");
        return switch (sourceType) {
            case FAMILY -> validateFamily(
                    currentUser, sourceOrganizationId, requestedReviewerUserId, draft);
            case ORGANIZATION -> validateOrganization(
                    currentUser, sourceOrganizationId, requestedReviewerUserId, draft);
            case TEACHER -> validateTeacher(
                    currentUser, sourceOrganizationId, requestedReviewerUserId, draft);
        };
    }

    private boolean selectedFamilyTaskAllowed(LearningTask task) {
        Long selected = com.lingdong.learning.common.security.SelectedOrganizationContext.organizationId();
        return selected == null || selectedTaskTargets.existsStudentInOrganization(task.id(), selected);
    }
    public void requireManageable(AuthenticatedUser currentUser, LearningTask task) {
        if (currentUser == null || task == null) {
            throw notFound();
        }
        if (task.sourceType()!=LearningTaskSourceType.FAMILY && !SelectedTaskInstitutionScope.allows(organizationMapper,organizationMapper.findById(task.sourceOrganizationId()))) throw notFound();
        boolean manageable = switch (task.sourceType()) {
            case FAMILY -> hasRole(currentUser, "PARENT")
                    && task.creatorUserId().equals(currentUser.userId()) && selectedFamilyTaskAllowed(task);
            case TEACHER -> teacherSourceAllowed(currentUser, task.sourceOrganizationId())
                    && task.creatorUserId().equals(currentUser.userId());
            case ORGANIZATION -> hasRole(currentUser, "ORG_ADMIN")
                    && task.sourceOrganizationId() != null
                    && organizationDataScopeService.canAccess(
                    currentUser.userId(), task.sourceOrganizationId());
        };
        if (!manageable) {
            throw notFound();
        }
    }

    /** 任务管理者或任务所涉学生的活动家长可以读取已发布任务。 */
    public void requireReadable(AuthenticatedUser currentUser, LearningTask task) {
        if (currentUser == null || task == null) {
            throw notFound();
        }
        if (task.status() == LearningTaskStatus.PUBLISHED
                && hasRole(currentUser, "PARENT")
                && assignmentMapper.existsTaskAssignedToActiveParent(
                task.id(), currentUser.userId())) {
            return;
        }
        requireManageable(currentUser, task);
    }

    /** 任务创建者及本班已接收机构任务的教师可查看学生进度。 */
    public void requireProgressReadable(AuthenticatedUser currentUser, LearningTask task) {
        if (currentUser == null || task == null || task.sourceType() == LearningTaskSourceType.FAMILY) {
            throw notFound();
        }
        if (hasOrganizationAccess(currentUser, "LEARNING_TASK_PROGRESS_READ") && task.sourceType() == LearningTaskSourceType.ORGANIZATION
                && task.sourceOrganizationId() != null
                && organizationDataScopeService.canAccess(currentUser.userId(), task.sourceOrganizationId())) {
            return;
        }
        if (isTeacher(currentUser)
                && (task.sourceType() == LearningTaskSourceType.TEACHER
                    && teacherSourceAllowed(currentUser, task.sourceOrganizationId())
                    && task.creatorUserId().equals(currentUser.userId())
                || task.status() == LearningTaskStatus.PUBLISHED
                && task.sourceType() == LearningTaskSourceType.ORGANIZATION
                && assignmentMapper.existsTaskVisibleToActiveTeacher(
                        task.id(), currentUser.userId()))) {
            return;
        }
        throw notFound();
    }

    private Long validateFamily(
            AuthenticatedUser currentUser,
            Long sourceOrganizationId,
            Long requestedReviewerUserId,
            ValidatedLearningTaskDraft draft
    ) {
        requireRole(currentUser, "PARENT");
        if (sourceOrganizationId != null) {
            throw new IllegalArgumentException("家庭任务不能指定来源组织");
        }
        if (requestedReviewerUserId != null
                && !requestedReviewerUserId.equals(currentUser.userId())) {
            throw new IllegalArgumentException("家庭任务审核人必须是当前家长");
        }
        for (LearningTaskTargetInput target : draft.targets()) {
            if (target.targetType() != LearningTaskTargetType.STUDENT) {
                throw new IllegalArgumentException("家庭任务只能选择学生目标");
            }
            if (!parentStudentMapper.existsActivePrimaryByParentAndStudent(
                    currentUser.userId(), target.targetId())) {
                throw notFound();
            }
        }
        return currentUser.userId();
    }

    private Long validateOrganization(
            AuthenticatedUser currentUser,
            Long sourceOrganizationId,
            Long requestedReviewerUserId,
            ValidatedLearningTaskDraft draft
    ) {
        requireRole(currentUser, "ORG_ADMIN");
        Organization source = requireEnabledAccessibleOrganization(currentUser, sourceOrganizationId);
        Long reviewerUserId = requestedReviewerUserId == null
                ? currentUser.userId() : requestedReviewerUserId;
        boolean delegatedToTeacher = !reviewerUserId.equals(currentUser.userId());
        if (delegatedToTeacher) {
            requireEnabledTeacher(reviewerUserId);
        }

        for (LearningTaskTargetInput target : draft.targets()) {
            if (target.targetType() == LearningTaskTargetType.ORGANIZATION) {
                Organization targetOrganization = organizationMapper.findById(target.targetId());
                if (targetOrganization == null
                        || !OrganizationOperationalStatusService.isOperational(targetOrganization)
                        || !targetOrganization.path().startsWith(source.path())) {
                    throw notFound();
                }
                if (delegatedToTeacher) {
                    requireActiveTeacherClass(reviewerUserId, targetOrganization);
                }
            } else if (!studentOrganizationMapper.existsActiveStudentInOrganizationSubtree(
                    target.targetId(), source.id())) {
                throw notFound();
            } else if (delegatedToTeacher
                    && !studentOrganizationMapper.existsActiveStudentInTeacherClasses(
                    target.targetId(), reviewerUserId)) {
                throw new IllegalArgumentException("审核教师不能审核所选学生");
            }
        }
        return reviewerUserId;
    }

    private Long validateTeacher(
            AuthenticatedUser currentUser,
            Long sourceOrganizationId,
            Long requestedReviewerUserId,
            ValidatedLearningTaskDraft draft
    ) {
        requireRole(currentUser, "TEACHER");
        Organization source = organizationMapper.findById(sourceOrganizationId);
        if (!SelectedTaskInstitutionScope.allows(organizationMapper,source) || !OrganizationOperationalStatusService.isOperational(source)
                || !"CLASS".equals(source.typeCode())) {
            throw notFound();
        }
        requireActiveTeacherClass(currentUser.userId(), source);
        if (requestedReviewerUserId != null
                && !requestedReviewerUserId.equals(currentUser.userId())) {
            throw new IllegalArgumentException("教师任务审核人必须是当前教师");
        }

        for (LearningTaskTargetInput target : draft.targets()) {
            if (target.targetType() == LearningTaskTargetType.ORGANIZATION) {
                if (!source.id().equals(target.targetId())) {
                    throw notFound();
                }
            } else if (!studentOrganizationMapper.existsActiveClass(
                    target.targetId(), source.id())) {
                throw notFound();
            }
        }
        return currentUser.userId();
    }

    private Organization requireEnabledAccessibleOrganization(
            AuthenticatedUser currentUser, Long organizationId
    ) {
        if (organizationId == null) {
            throw new IllegalArgumentException("机构任务来源组织不能为空");
        }
        Organization organization = organizationMapper.findById(organizationId);
        if (!SelectedTaskInstitutionScope.allows(organizationMapper,organization) || !OrganizationOperationalStatusService.isOperational(organization)
                || !organizationDataScopeService.canAccess(currentUser.userId(), organizationId)) {
            throw notFound();
        }
        return organization;
    }

    private void requireEnabledTeacher(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED
                || !teacherIdentity.isTeacher(userId)) {
            throw new IllegalArgumentException("审核教师不存在或不可用");
        }
    }

    private void requireActiveTeacherClass(Long teacherUserId, Organization organization) {
        if (!"CLASS".equals(organization.typeCode())) {
            throw new IllegalArgumentException("委派教师审核时目标组织必须是班级");
        }
        TeacherClassRelation relation = teacherClassMapper.findByTeacherAndClass(
                teacherUserId, organization.id());
        if (relation == null || relation.status() != TeacherClassStatus.ACTIVE) {
            throw new IllegalArgumentException("审核教师未关联所选班级");
        }
    }

    private void requireRole(AuthenticatedUser currentUser, String roleCode) {
        if (!hasRole(currentUser, roleCode)) {
            throw new SystemOperationAccessDeniedException("当前角色不能使用该任务来源");
        }
    }

    /** Same source identity rules as task creation; actions/features remain independently effective capabilities. */
    public java.util.List<String> allowedSourceTypes(AuthenticatedUser user) {
        var result=new java.util.ArrayList<String>();
        if (isParent(user)) result.add(LearningTaskSourceType.FAMILY.name());
        if (isTeacher(user)) result.add(LearningTaskSourceType.TEACHER.name());
        if (isOrganizationOperator(user)) {
            var scope=organizationScope(user);
            if (scope.allOrganizations() || !scope.rootPaths().isEmpty()) result.add(LearningTaskSourceType.ORGANIZATION.name());
        }
        return java.util.List.copyOf(result);
    }
    public boolean isParent(AuthenticatedUser user) {
        try { parentActors.require(user); return true; }
        catch (SystemOperationAccessDeniedException exception) { return false; }
    }
    public boolean isTeacher(AuthenticatedUser user) {
        return user != null && teacherIdentity.isTeacher(user.userId())
                && teacherClassMapper.findActiveByTeacher(user.userId()).stream().anyMatch(relation ->
                    relation.status() == TeacherClassStatus.ACTIVE
                    && OrganizationOperationalStatusService.isOperational(organizationMapper.findById(relation.classOrganizationId())));
    }
    public boolean teacherSourceAllowed(AuthenticatedUser user, Long sourceOrganizationId) {
        if (!isTeacher(user) || sourceOrganizationId==null) return false;
        var relation=teacherClassMapper.findByTeacherAndClass(user.userId(),sourceOrganizationId);
        return relation!=null && relation.status()==TeacherClassStatus.ACTIVE
                && OrganizationOperationalStatusService.isOperational(organizationMapper.findById(sourceOrganizationId));
    }
    public boolean isOrganizationOperator(AuthenticatedUser user) {
        var account = user == null ? null : userMapper.findById(user.userId());
        return account != null && account.status() == UserStatus.ENABLED
                && (account.type() == com.lingdong.learning.user.domain.UserType.ORGANIZATION
                || account.type() == com.lingdong.learning.user.domain.UserType.PLATFORM
                || account.type() != com.lingdong.learning.user.domain.UserType.STUDENT
                    && userRoleMapper.hasRoleCode(account.id(), "ORG_ADMIN"));
    }
    private boolean hasRole(AuthenticatedUser user, String identity) {
        return switch (identity) {
            case "PARENT" -> isParent(user);
            case "TEACHER" -> isTeacher(user);
            case "ORG_ADMIN" -> isOrganizationOperator(user);
            default -> false;
        };
    }
    public boolean hasOrganizationPermission(AuthenticatedUser user, String code) {
        return isOrganizationOperator(user) && user.clientType() != null
                && permissions.isAllowed(user.userId(),
                com.lingdong.learning.permission.domain.PermissionClient.valueOf(user.clientType().name()),code);
    }
    public boolean hasOrganizationAccess(AuthenticatedUser user, String code) {
        return hasOrganizationPermission(user,code);
    }

    public boolean canReadOrganizationProgress(AuthenticatedUser user, LearningTask task) {
        return task != null && task.sourceType() == LearningTaskSourceType.ORGANIZATION
                && task.sourceOrganizationId() != null && hasOrganizationAccess(user, "LEARNING_TASK_PROGRESS_READ")
                && organizationDataScopeService.canAccess(user.userId(), task.sourceOrganizationId());
    }

    public void requireAnyPermission(AuthenticatedUser user, java.util.List<String> codes) {
        if (user == null || user.clientType()==null || codes.stream().noneMatch(code -> permissions.isAllowed(
                user.userId(), com.lingdong.learning.permission.domain.PermissionClient.valueOf(user.clientType().name()),code)))
            throw new SystemOperationAccessDeniedException("当前账号无候选查询对应操作权限");
    }
    public void requireWebPermission(AuthenticatedUser user, String code) {
        if (user == null || user.clientType() == null || !permissions.isAllowed(user.userId(),
                com.lingdong.learning.permission.domain.PermissionClient.valueOf(user.clientType().name()),code))
            throw new SystemOperationAccessDeniedException("当前账号无操作权限："+code);
    }

    public com.lingdong.learning.datascope.application.OrganizationDataScope organizationScope(AuthenticatedUser user) {
        return organizationDataScopeService.resolve(user.userId());
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("任务资源不存在或不可访问");
    }
}




