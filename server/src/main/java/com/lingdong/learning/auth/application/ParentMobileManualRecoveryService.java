package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentMobileManualRecoveryCandidateRow;
import com.lingdong.learning.auth.infrastructure.persistence.ParentMobileManualRecoveryMapper;
import com.lingdong.learning.auth.infrastructure.security.ParentSmsCodeHasher;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
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

/** 在机构组织范围内执行家长原手机号不可用时的人工核验换绑。 */
@Service
public class ParentMobileManualRecoveryService {
    private static final String FEATURE_CODE = "PARENT_MOBILE_MANUAL_RECOVERY";
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
    private static final String PARENT_ROLE = "PARENT";
    private static final String CONFIRMATION = "已完成线下身份核验";

    private final ParentMobileManualRecoveryMapper recoveryMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final StudentMapper studentMapper;
    private final ParentStudentMapper relationshipMapper;
    private final ParentAccountLifecycleMapper lifecycleMapper;
    private final ParentSmsVerificationService smsService;
    private final ParentSmsCodeHasher smsCodeHasher;
    private final DeviceSessionMapper sessionMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ParentMobileManualRecoveryService(
            ParentMobileManualRecoveryMapper recoveryMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            StudentMapper studentMapper,
            ParentStudentMapper relationshipMapper,
            ParentAccountLifecycleMapper lifecycleMapper,
            ParentSmsVerificationService smsService,
            ParentSmsCodeHasher smsCodeHasher,
            DeviceSessionMapper sessionMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.recoveryMapper = recoveryMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.studentMapper = studentMapper;
        this.relationshipMapper = relationshipMapper;
        this.lifecycleMapper = lifecycleMapper;
        this.smsService = smsService;
        this.smsCodeHasher = smsCodeHasher;
        this.sessionMapper = sessionMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ParentMobileManualRecoveryCandidate> listCandidates(AuthenticatedUser currentUser) {
        requireOrganizationAdministrator(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return recoveryMapper.findCandidatesByOrganizationAdministrator(currentUser.userId())
                .stream()
                .map(this::candidate)
                .toList();
    }

    public IssuedParentSmsCode issueCode(
            AuthenticatedUser currentUser,
            IssueParentMobileManualRecoveryCodeCommand command
    ) {
        requireOrganizationAdministrator(currentUser);
        Objects.requireNonNull(command, "人工换绑验证码请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Long studentId = requiredId(command.studentId(), "学生标识");
        Long parentUserId = requiredId(command.parentUserId(), "家长用户标识");
        ParentMobileManualRecoveryCandidateRow candidate = recoveryMapper.findAccessibleCandidate(
                currentUser.userId(), studentId, parentUserId);
        if (candidate == null) {
            throw notFound();
        }
        requireAvailableNewMobileForIssue(candidate.mobile(), command.newMobile());
        return smsService.issue(
                command.newMobile(), ParentSmsPurpose.MANUAL_MOBILE_RECOVERY_NEW,
                currentUser.clientType(), command.sourceDigest());
    }

    @Transactional
    public void recover(AuthenticatedUser currentUser, ParentMobileManualRecoveryCommand command) {
        requireOrganizationAdministrator(currentUser);
        Objects.requireNonNull(command, "人工换绑请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Long studentId = requiredId(command.studentId(), "学生标识");
        Long parentUserId = requiredId(command.parentUserId(), "家长用户标识");
        String reason = requiredReason(command.reason());
        if (!CONFIRMATION.equals(command.confirmation())) {
            throw new IllegalArgumentException("请输入已完成线下身份核验");
        }

        User parent = userMapper.findByIdForUpdate(parentUserId);
        if (!isEnabledParent(parent) || !userRoleMapper.hasRoleCode(parentUserId, PARENT_ROLE)) {
            throw notFound();
        }
        if (lifecycleMapper.findActiveCancellation(parentUserId) != null) {
            throw new ParentAccountCancellationConflictException("目标家长存在活动注销申请，不能人工换绑");
        }
        Student student = studentMapper.findByIdForUpdate(studentId);
        ParentRelationship relationship = relationshipMapper.findByParentAndStudentForUpdate(
                parentUserId, studentId);
        if (student == null || student.status() != StudentStatus.ENABLED
                || relationship == null || !"ACTIVE".equals(relationship.status())) {
            throw notFound();
        }
        Long organizationId = recoveryMapper.findAccessibleOrganizationId(
                currentUser.userId(), studentId);
        if (organizationId == null) {
            throw notFound();
        }
        requireAvailableNewMobile(parent, command.newMobile());

        smsService.verifyAndConsume(
                command.newMobile(), ParentSmsPurpose.MANUAL_MOBILE_RECOVERY_NEW,
                currentUser.clientType(), command.smsCode());
        LocalDateTime recoveredAt = LocalDateTime.now(clock);
        ParentMobileManualRecoveryRecord audit = new ParentMobileManualRecoveryRecord(
                idGenerator.nextId(), parentUserId, studentId, organizationId,
                currentUser.userId(), smsCodeHasher.mobileDigest(parent.mobile()),
                smsCodeHasher.mobileDigest(command.newMobile()), reason,
                currentUser.clientType(), recoveredAt);
        try {
            if (userMapper.updateMobileIfExpected(
                    parentUserId, parent.mobile(), command.newMobile()) != 1) {
                throw new ParentMobileChangeConflictException();
            }
            if (recoveryMapper.insert(audit) != 1) {
                throw new IllegalStateException("人工换绑审计保存失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ParentMobileChangeConflictException(exception);
        }
        sessionMapper.revokeAllActiveByUserId(parentUserId, recoveredAt);
    }

    private ParentMobileManualRecoveryCandidate candidate(
            ParentMobileManualRecoveryCandidateRow row
    ) {
        return new ParentMobileManualRecoveryCandidate(
                row.studentId(), row.studentName(), row.organizationId(), row.organizationName(),
                row.parentUserId(), row.parentDisplayName(), maskMobile(row.mobile()),
                row.relationshipRole());
    }

    private void requireOrganizationAdministrator(AuthenticatedUser currentUser) {
        if (currentUser == null || !currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)
                || (currentUser.clientType() != AuthClientType.WEB
                && currentUser.clientType() != AuthClientType.MINIAPP)) {
            throw new ResourceNotFoundException("人工换绑对象不存在或不可访问");
        }
    }

    private boolean isEnabledParent(User user) {
        return user != null && user.type() == UserType.FAMILY
                && user.status() == UserStatus.ENABLED && user.mobile() != null;
    }

    private void requireAvailableNewMobileForIssue(String oldMobile, String newMobile) {
        if (newMobile == null || newMobile.equals(oldMobile) || userMapper.existsByMobile(newMobile)) {
            throw new ParentMobileChangeConflictException();
        }
    }

    private void requireAvailableNewMobile(User parent, String newMobile) {
        if (newMobile == null || newMobile.equals(parent.mobile())) {
            throw new ParentMobileChangeConflictException();
        }
        User occupied = userMapper.findByMobileForUpdate(newMobile);
        boolean loginNameMustFollowMobile = parent.username().equals(parent.mobile());
        if (occupied != null || (loginNameMustFollowMobile && userMapper.existsByUsername(newMobile))) {
            throw new ParentMobileChangeConflictException();
        }
    }

    private Long requiredId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("核验原因不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("核验原因不能超过200个字符");
        }
        return normalized;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("人工换绑对象不存在或不可访问");
    }
}
