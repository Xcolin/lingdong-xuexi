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
            com.lingdong.learning.permission.application.PermissionDecisionService permissions
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

    public void requireManageable(AuthenticatedUser currentUser, LearningTask task) {
        if (currentUser == null || task == null) {
            throw notFound();
        }
        boolean manageable = switch (task.sourceType()) {
            case FAMILY -> hasRole(currentUser, "PARENT")
                    && task.creatorUserId().equals(currentUser.userId());
            case TEACHER -> hasRole(currentUser, "TEACHER")
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
        if (hasRole(currentUser, "TEACHER")
                && (task.creatorUserId().equals(currentUser.userId())
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
        if (currentUser != null && currentUser.clientType() == com.lingdong.learning.auth.domain.AuthClientType.WEB) {
            if (!hasAnyTaskPermission(currentUser)) throw notFound();
        } else requireRole(currentUser, "ORG_ADMIN");
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
        if (!OrganizationOperationalStatusService.isOperational(source)
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
        if (!OrganizationOperationalStatusService.isOperational(organization)
                || !organizationDataScopeService.canAccess(currentUser.userId(), organizationId)) {
            throw notFound();
        }
        return organization;
    }

    private void requireEnabledTeacher(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED
                || !userRoleMapper.hasRoleCode(userId, "TEACHER")) {
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

    private boolean hasRole(AuthenticatedUser currentUser, String roleCode) {
        if ("ORG_ADMIN".equals(roleCode) && currentUser != null
                && currentUser.clientType() == com.lingdong.learning.auth.domain.AuthClientType.WEB) {
            return hasAnyTaskPermission(currentUser);
        }
        // 审核员仅处理系统审批，兼任业务角色也不能进入任务业务范围。
        return currentUser != null && (currentUser.clientType() == com.lingdong.learning.auth.domain.AuthClientType.WEB
                || !currentUser.roleCodes().contains("SYS_AUDITOR"))
                && (currentUser.clientType() != com.lingdong.learning.auth.domain.AuthClientType.WEB
                    || hasAnyTaskPermission(currentUser))
                && currentUser.roleCodes().contains(roleCode);
    }

    public boolean hasOrganizationPermission(AuthenticatedUser user, String code) {
        return user != null && user.clientType() == com.lingdong.learning.auth.domain.AuthClientType.WEB
                && permissions.isAllowed(user.userId(), com.lingdong.learning.permission.domain.PermissionClient.WEB, code);
    }

    public boolean hasOrganizationAccess(AuthenticatedUser user, String code) {
        return hasOrganizationPermission(user, code) || user != null
                && user.clientType() == com.lingdong.learning.auth.domain.AuthClientType.MINIAPP
                && user.roleCodes().contains("ORG_ADMIN") && !user.roleCodes().contains("SYS_AUDITOR");
    }

    public boolean canReadOrganizationProgress(AuthenticatedUser user, LearningTask task) {
        return task != null && task.sourceType() == LearningTaskSourceType.ORGANIZATION
                && task.sourceOrganizationId() != null && hasOrganizationAccess(user, "LEARNING_TASK_PROGRESS_READ")
                && organizationDataScopeService.canAccess(user.userId(), task.sourceOrganizationId());
    }

    private boolean hasAnyTaskPermission(AuthenticatedUser user) {
        return java.util.List.of("LEARNING_TASK_READ_MANAGED", "LEARNING_TASK_CREATE", "LEARNING_TASK_PUBLISH",
                "LEARNING_TASK_PROGRESS_READ").stream().anyMatch(code -> hasOrganizationPermission(user, code));
    }

    public void requireWebPermission(AuthenticatedUser user, String code) {
        if (user == null || user.clientType() == com.lingdong.learning.auth.domain.AuthClientType.WEB
                && !hasOrganizationPermission(user, code)) throw new SystemOperationAccessDeniedException("当前账号无操作权限：" + code);
    }

    public com.lingdong.learning.datascope.application.OrganizationDataScope organizationScope(AuthenticatedUser user) {
        return organizationDataScopeService.resolve(user.userId());
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("任务资源不存在或不可访问");
    }
}

