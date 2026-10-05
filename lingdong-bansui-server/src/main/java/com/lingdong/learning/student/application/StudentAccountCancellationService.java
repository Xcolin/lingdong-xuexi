package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.StudentQrTicketMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentAccountCancellationCandidateRow;
import com.lingdong.learning.student.infrastructure.persistence.StudentAccountCancellationMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentCredentialMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 在原机构数据范围内执行学生账号的不可恢复注销。 */
@Service
public class StudentAccountCancellationService {
    private final StudentManagementAccessService managementAccess;
    private static final String FEATURE_CODE = "STUDENT_ACCOUNT_CANCELLATION";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
    private static final String STUDENT_ROLE = "STUDENT";
    private static final String CONFIRMATION = "确认注销学生账号";

    private final StudentAccountCancellationMapper cancellationMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentOrganizationMapper organizationMapper;
    private final StudentCredentialMapper credentialMapper;
    private final StudentQrTicketMapper qrTicketMapper;
    private final DeviceSessionMapper sessionMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public StudentAccountCancellationService(
            StudentAccountCancellationMapper cancellationMapper,
            StudentMapper studentMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            ParentStudentMapper parentStudentMapper,
            StudentOrganizationMapper organizationMapper,
            StudentCredentialMapper credentialMapper,
            StudentQrTicketMapper qrTicketMapper,
            DeviceSessionMapper sessionMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock, StudentManagementAccessService managementAccess
    ) {
        this.managementAccess = managementAccess;
        this.cancellationMapper = cancellationMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.organizationMapper = organizationMapper;
        this.credentialMapper = credentialMapper;
        this.qrTicketMapper = qrTicketMapper;
        this.sessionMapper = sessionMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentAccountCancellationCandidate> listCandidates(AuthenticatedUser currentUser) {
        requireOrganizationAdministrator(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return cancellationMapper.findCandidatesByOrganizationScope(managementAccess.scope(currentUser))
                .stream()
                .map(this::candidate)
                .toList();
    }

    @Transactional
    public void cancel(AuthenticatedUser currentUser, StudentAccountCancellationCommand command) {
        requireOrganizationAdministrator(currentUser);
        Objects.requireNonNull(command, "学生账号注销请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Long studentId = requiredId(command.studentId());
        String reason = requiredReason(command.reason());
        if (!CONFIRMATION.equals(command.confirmation())) {
            throw new IllegalArgumentException("请输入确认注销学生账号");
        }

        Student student = studentMapper.findByIdForUpdate(studentId);
        if (!isEnabledStudent(student)) {
            throw notFound();
        }
        Long studentUserId = student.studentUserId();
        User studentUser = userMapper.findByIdForUpdate(studentUserId);
        if (!isEnabledStudentUser(studentUser)
                || !userRoleMapper.hasRoleCode(studentUserId, STUDENT_ROLE)) {
            throw notFound();
        }
        if (!organizationMapper.findActiveOrganizationIdsForUpdate(studentId).isEmpty()
                || !parentStudentMapper.findActiveByStudentIdForUpdate(studentId).isEmpty()) {
            throw new StudentAccountCancellationConflictException("学生仍存在活动机构或家长关系");
        }
        Long organizationId = cancellationMapper
                .findAccessibleLatestInactiveEnrollmentOrganizationIdByScope(managementAccess.scope(currentUser), studentId);
        if (organizationId == null || organizationId <= 0) {
            throw notFound();
        }

        LocalDateTime cancelledAt = LocalDateTime.now(clock);
        StudentAccountCancellationRecord record = new StudentAccountCancellationRecord(
                idGenerator.nextId(), studentId, studentUserId, organizationId,
                currentUser.userId(), reason, currentUser.clientType(), cancelledAt);
        try {
            if (studentMapper.anonymizeCancelledStudent(studentId) != 1
                    || userMapper.anonymizeCancelledStudent(
                            studentUserId, "cancelled_student_" + studentId) != 1) {
                throw new StudentAccountCancellationConflictException("学生账号状态已发生变化");
            }
            credentialMapper.deleteByStudentUserId(studentUserId);
            qrTicketMapper.revokeActiveByStudentId(studentId);
            sessionMapper.revokeAllActiveByUserId(studentUserId, cancelledAt);
            if (cancellationMapper.insert(record) != 1) {
                throw new IllegalStateException("学生账号注销审计保存失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new StudentAccountCancellationConflictException(exception);
        }
    }

    private StudentAccountCancellationCandidate candidate(StudentAccountCancellationCandidateRow row) {
        return new StudentAccountCancellationCandidate(
                row.studentId(), row.studentName(), row.studentAccount(),
                row.organizationId(), row.organizationName());
    }

    private void requireOrganizationAdministrator(AuthenticatedUser currentUser) {
        if (currentUser == null || (currentUser.clientType() == AuthClientType.WEB
                ? !managementAccess.webAllowed(currentUser, "STUDENT_ACCOUNT_CANCELLATION_MANAGE")
                : !currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE))
                || (currentUser.clientType() != AuthClientType.WEB
                && currentUser.clientType() != AuthClientType.MINIAPP)) {
            throw notFound();
        }
    }

    private boolean isEnabledStudent(Student student) {
        return student != null && student.status() == StudentStatus.ENABLED
                && student.studentUserId() != null;
    }

    private boolean isEnabledStudentUser(User user) {
        return user != null && user.type() == UserType.STUDENT
                && user.status() == UserStatus.ENABLED;
    }

    private Long requiredId(Long studentId) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("学生标识不能为空");
        }
        return studentId;
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("注销原因不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("注销原因不能超过200个字符");
        }
        return normalized;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("学生账号不存在或不可访问");
    }
}
