package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.PasswordPolicy;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.learningtask.application.TeacherClassAssignmentService;
import com.lingdong.learning.learningtask.application.TeacherReviewAutoTransferService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherClassIdRow;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryCriteria;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherDirectoryRow;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherManagementMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.DuplicateUserAccountException;
import com.lingdong.learning.user.application.UpdateUserStatusCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 协调既有用户、组织、角色和班级关系完成教师账号维护。 */
@Service
public class TeacherManagementApplicationService {
    private static final String TEACHER_ROLE_CODE = "TEACHER";

    private final TeacherManagementAccessService accessService;
    private final UserAccessApplicationService userAccessApplicationService;
    private final TeacherClassAssignmentService teacherClassAssignmentService;
    private final TeacherManagementMapper teacherManagementMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final OrganizationMapper organizationMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationApplicationService authenticationApplicationService;
    private final IamChangeAuditService iamChangeAuditService;
    private final TeacherReviewAutoTransferService reviewAutoTransferService;
    private final PermissionDecisionService permissionDecisionService;

    public TeacherManagementApplicationService(
            TeacherManagementAccessService accessService,
            UserAccessApplicationService userAccessApplicationService,
            TeacherClassAssignmentService teacherClassAssignmentService,
            TeacherManagementMapper teacherManagementMapper,
            OrganizationDataScopeService organizationDataScopeService,
            OrganizationMapper organizationMapper,
            RoleMapper roleMapper,
            UserMapper userMapper,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            AuthenticationApplicationService authenticationApplicationService,
            IamChangeAuditService iamChangeAuditService,
            TeacherReviewAutoTransferService reviewAutoTransferService,
            PermissionDecisionService permissionDecisionService
    ) {
        this.accessService = accessService;
        this.userAccessApplicationService = userAccessApplicationService;
        this.teacherClassAssignmentService = teacherClassAssignmentService;
        this.teacherManagementMapper = teacherManagementMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.organizationMapper = organizationMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.authenticationApplicationService = authenticationApplicationService;
        this.iamChangeAuditService = iamChangeAuditService;
        this.reviewAutoTransferService = reviewAutoTransferService;
        this.permissionDecisionService = permissionDecisionService;
    }

    /** 在一个事务中创建教师身份及其初始班级范围。 */
    @Transactional
    public TeacherAccount create(AuthenticatedUser currentUser, CreateTeacherCommand command) {
        Objects.requireNonNull(command, "创建教师请求不能为空");
        Organization school = accessService.requireManageableSchool(currentUser, command.schoolId());
        String displayName = requiredText(command.displayName(), "教师姓名", 20);
        passwordPolicy.validate(command.initialPassword());

        if (!command.classOrganizationIds().isEmpty()) {
            requirePermission(currentUser, "TEACHER_CLASS_ASSIGN");
        }

        List<Organization> classes = new LinkedHashSet<>(command.classOrganizationIds()).stream()
                .map(classId -> accessService.requireManageableClass(currentUser, school, classId))
                .toList();

        User user = userAccessApplicationService.createUser(new CreateUserCommand(
                command.username(), displayName, command.mobile(), UserType.ORGANIZATION,
                currentUser.userId()));
        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(
                user.id(), school.id(), currentUser.userId()));
        Role teacherRole = roleMapper.findByCode(TEACHER_ROLE_CODE);
        if (teacherRole == null) {
            throw new IllegalStateException("内置教师角色不存在");
        }
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(
                user.id(), teacherRole.id(), null, currentUser.userId()));
        if (userMapper.updatePasswordHash(
                user.id(), passwordEncoder.encode(command.initialPassword())) != 1) {
            throw new IllegalStateException("教师初始密码保存失败");
        }
        for (Organization classOrganization : classes) {
            teacherClassAssignmentService.assign(currentUser, user.id(), classOrganization.id());
        }

        User created = userMapper.findById(user.id());
        return new TeacherAccount(
                created.id(), created.username(), created.displayName(), created.mobile(), created.status(),
                school.id(), school.name(), classes.stream().map(Organization::id).toList(),
                created.createdAt(), created.updatedAt());
    }

    private void requirePermission(AuthenticatedUser currentUser, String permissionCode) {
        PermissionClient client = PermissionClient.valueOf(currentUser.clientType().name());
        if (!permissionDecisionService.isAllowed(currentUser.userId(), client, permissionCode)) {
            throw new SystemOperationAccessDeniedException("无权执行教师班级分配操作");
        }
    }

    /** 在数据库分页前应用组织根路径，避免范围外教师影响页数和总数。 */
    public TeacherPage list(AuthenticatedUser currentUser, TeacherQuery query) {
        Objects.requireNonNull(query, "教师目录查询不能为空");
        accessService.requireManager(currentUser);
        int page = query.page() < 1 ? 1 : query.page();
        int pageSize = Math.min(Math.max(query.pageSize(), 1), 100);
        String keyword = optionalText(query.keyword(), 64);
        if (query.schoolId() != null) {
            accessService.requireManageableSchool(currentUser, query.schoolId());
        }
        if (query.classOrganizationId() != null) {
            Organization classOrganization = organizationMapper.findById(query.classOrganizationId());
            if (classOrganization == null
                    || !"CLASS".equals(classOrganization.typeCode())
                    || !organizationDataScopeService.canAccess(currentUser.userId(), classOrganization.id())) {
                throw new com.lingdong.learning.common.web.ResourceNotFoundException(
                        "教师、学校或班级不存在或不可访问");
            }
        }

        OrganizationDataScope scope = organizationDataScopeService.resolve(currentUser.userId());
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) {
            return new TeacherPage(List.of(), page, pageSize, 0);
        }
        TeacherDirectoryCriteria criteria = new TeacherDirectoryCriteria(
                keyword, query.schoolId(), query.classOrganizationId(), query.status(),
                scope.allOrganizations(), scope.rootPaths(), pageSize, (page - 1) * pageSize);
        List<TeacherDirectoryRow> rows = teacherManagementMapper.findPage(criteria);
        Map<Long, List<Long>> classIds = rows.isEmpty() ? Map.of() : teacherManagementMapper
                .findActiveClassIds(rows.stream().map(TeacherDirectoryRow::id).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        TeacherClassIdRow::teacherUserId,
                        Collectors.mapping(TeacherClassIdRow::classOrganizationId, Collectors.toList())));
        List<TeacherAccount> items = rows.stream()
                .map(row -> new TeacherAccount(
                        row.id(), row.username(), row.displayName(), row.mobile(), row.status(),
                        row.schoolId(), row.schoolName(), classIds.getOrDefault(row.id(), List.of()),
                        row.createdAt(), row.updatedAt()))
                .toList();
        return new TeacherPage(items, page, pageSize, teacherManagementMapper.count(criteria));
    }

    /** 查询组织范围内单个教师的安全详情。 */
    public TeacherAccount get(AuthenticatedUser currentUser, Long teacherUserId) {
        return toAccount(requireAccessibleTeacher(currentUser, teacherUserId));
    }

    /** 修改教师姓名和手机号，账号与学校关系保持不可变。 */
    @Transactional
    public TeacherAccount updateProfile(
            AuthenticatedUser currentUser,
            Long teacherUserId,
            UpdateTeacherProfileCommand command
    ) {
        Objects.requireNonNull(command, "教师资料修改请求不能为空");
        TeacherDirectoryRow current = requireAccessibleTeacher(currentUser, teacherUserId);
        String displayName = requiredText(command.displayName(), "教师姓名", 20);
        String submittedMobile = optionalText(command.mobile(), 32);
        if (command.clearMobile() && submittedMobile != null) {
            throw new IllegalArgumentException("清空手机号时不能同时提交新手机号");
        }
        String mobile = command.clearMobile() ? null
                : submittedMobile == null ? current.mobile() : submittedMobile;
        if (mobile != null && teacherManagementMapper.existsMobileExcludingId(mobile, teacherUserId)) {
            throw new DuplicateUserAccountException(mobile);
        }
        try {
            if (teacherManagementMapper.updateProfile(teacherUserId, displayName, mobile) != 1) {
                throw new IllegalStateException("教师资料更新失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserAccountException(mobile);
        }
        iamChangeAuditService.record(
                IamChangeAuditEventType.USER_PROFILE_CHANGE,
                currentUser.userId(),
                IamChangeTargetType.USER,
                teacherUserId,
                null,
                current.schoolId(),
                null,
                null);
        return toAccount(requireAccessibleTeacher(currentUser, teacherUserId));
    }

    /** 重置教师密码并立即使该教师的全部既有会话失效。 */
    @Transactional
    public void resetPassword(AuthenticatedUser currentUser, Long teacherUserId, String newPassword) {
        TeacherDirectoryRow teacher = requireAccessibleTeacher(currentUser, teacherUserId);
        passwordPolicy.validate(newPassword);
        if (userMapper.updatePasswordHash(teacherUserId, passwordEncoder.encode(newPassword)) != 1) {
            throw new IllegalStateException("教师密码重置失败");
        }
        authenticationApplicationService.revokeAllActiveSessionsForUser(teacherUserId);
        iamChangeAuditService.record(
                IamChangeAuditEventType.USER_PASSWORD_RESET,
                currentUser.userId(),
                IamChangeTargetType.USER,
                teacherUserId,
                null,
                teacher.schoolId(),
                null,
                null);
    }

    /** 启用、停用或锁定教师；停用和锁定前保护未完成审核链。 */
    @Transactional
    public TeacherAccount changeStatus(
            AuthenticatedUser currentUser,
            Long teacherUserId,
            UserStatus targetStatus
    ) {
        TeacherDirectoryRow teacher = requireAccessibleTeacher(currentUser, teacherUserId);
        Objects.requireNonNull(targetStatus, "教师状态不能为空");
        if (targetStatus == UserStatus.CANCELLED) {
            throw new IllegalArgumentException("教师管理不支持注销账号");
        }
        if (targetStatus != UserStatus.ENABLED && targetStatus != teacher.status()) {
            reviewAutoTransferService.transferAll(currentUser, teacherUserId);
        }
        userAccessApplicationService.updateStatus(new UpdateUserStatusCommand(
                teacherUserId, targetStatus, currentUser.userId()));
        return toAccount(requireAccessibleTeacher(currentUser, teacherUserId));
    }

    /** 组织路径范围在 SQL 中裁剪，范围外目标统一按不存在处理。 */
    private TeacherDirectoryRow requireAccessibleTeacher(AuthenticatedUser currentUser, Long teacherUserId) {
        accessService.requireManager(currentUser);
        if (teacherUserId == null) {
            throw notFound();
        }
        OrganizationDataScope scope = organizationDataScopeService.resolve(currentUser.userId());
        if (!scope.allOrganizations() && scope.rootPaths().isEmpty()) {
            throw notFound();
        }
        TeacherDirectoryRow teacher = teacherManagementMapper.findAccessibleById(
                teacherUserId, scope.allOrganizations(), scope.rootPaths());
        if (teacher == null) {
            throw notFound();
        }
        return teacher;
    }

    private TeacherAccount toAccount(TeacherDirectoryRow row) {
        List<Long> classIds = teacherManagementMapper.findActiveClassIds(List.of(row.id())).stream()
                .map(TeacherClassIdRow::classOrganizationId)
                .toList();
        return new TeacherAccount(
                row.id(), row.username(), row.displayName(), row.mobile(), row.status(),
                row.schoolId(), row.schoolName(), classIds, row.createdAt(), row.updatedAt());
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("教师、学校或班级不存在或不可访问");
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("查询文本长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
