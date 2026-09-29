package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 按主家长或直接机构管理员范围初始化、重置学生登录凭证。 */
@Service
public class StudentCredentialManagementService {
    private static final String PARENT_ROLE = "PARENT";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";

    private final StudentMapper studentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentOrganizationMapper studentOrganizationMapper;
    private final StudentIdentityProvisioningService identityProvisioningService;
    private final AuthenticationApplicationService authenticationApplicationService;
    private final UserMapper userMapper;

    public StudentCredentialManagementService(
            StudentMapper studentMapper,
            ParentStudentMapper parentStudentMapper,
            StudentOrganizationMapper studentOrganizationMapper,
            StudentIdentityProvisioningService identityProvisioningService,
            AuthenticationApplicationService authenticationApplicationService,
            UserMapper userMapper
    ) {
        this.studentMapper = studentMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.studentOrganizationMapper = studentOrganizationMapper;
        this.identityProvisioningService = identityProvisioningService;
        this.authenticationApplicationService = authenticationApplicationService;
        this.userMapper = userMapper;
    }

    @Transactional
    public StudentCredentialIssueResult initialize(AuthenticatedUser currentUser, Long studentId) {
        Student student = requireScopedEnabledStudent(currentUser, studentId);
        if (student.studentUserId() != null) {
            throw new IllegalStateException("学生登录凭证已经初始化");
        }
        IssuedStudentCredential issued = identityProvisioningService.issue(student.studentName());
        if (studentMapper.bindStudentUserIfAbsent(student.id(), issued.studentUserId()) != 1) {
            throw new IllegalStateException("学生登录凭证已经初始化");
        }
        return new StudentCredentialIssueResult(issued.studentAccount(), issued.plainLoginCode());
    }

    @Transactional
    public StudentCredentialIssueResult resetLoginCode(AuthenticatedUser currentUser, Long studentId) {
        Student student = requireScopedEnabledStudent(currentUser, studentId);
        if (student.studentUserId() == null) {
            throw new IllegalStateException("学生登录凭证尚未初始化");
        }
        User studentUser = userMapper.findById(student.studentUserId());
        if (studentUser == null || studentUser.type() != UserType.STUDENT || studentUser.status() != UserStatus.ENABLED) {
            throw new IllegalStateException("学生账号当前不可重置登录码");
        }
        String plainLoginCode = identityProvisioningService.resetLoginCode(studentUser.id());
        authenticationApplicationService.revokeAllActiveSessionsForUser(studentUser.id());
        return new StudentCredentialIssueResult(studentUser.username(), plainLoginCode);
    }

    private Student requireScopedEnabledStudent(AuthenticatedUser currentUser, Long studentId) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        if (studentId == null) {
            throw new IllegalArgumentException("学生标识不能为空");
        }
        Student student = studentMapper.findById(studentId);
        if (student == null || !hasObjectScope(currentUser, studentId)) {
            throw new ResourceNotFoundException("学生档案不存在或无权访问");
        }
        if (student.status() != StudentStatus.ENABLED) {
            throw new IllegalStateException("学生档案已停用");
        }
        return student;
    }

    private boolean hasObjectScope(AuthenticatedUser currentUser, Long studentId) {
        boolean primaryParent = currentUser.roleCodes().contains(PARENT_ROLE)
                && parentStudentMapper.existsActivePrimaryByParentAndStudent(currentUser.userId(), studentId);
        boolean directOrganizationAdministrator = currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)
                && studentOrganizationMapper.existsActiveByOrganizationAdministratorAndStudent(
                currentUser.userId(), studentId);
        return primaryParent || directOrganizationAdministrator;
    }
}
