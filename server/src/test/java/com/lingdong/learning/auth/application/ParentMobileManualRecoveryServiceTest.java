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
import com.lingdong.learning.student.domain.ParentRelationshipRole;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentMobileManualRecoveryServiceTest {
    private static final long OPERATOR_ID = 1874244142494646901L;
    private static final long PARENT_ID = 1874244142494646902L;
    private static final long STUDENT_ID = 1874244142494646903L;
    private static final long ORGANIZATION_ID = 1874244142494646904L;
    private static final long AUDIT_ID = 1874244142494646905L;
    private static final String OLD_MOBILE = "13800138000";
    private static final String NEW_MOBILE = "13900139000";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 10, 0);

    private final ParentMobileManualRecoveryMapper recoveryMapper = mock(ParentMobileManualRecoveryMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final ParentStudentMapper relationshipMapper = mock(ParentStudentMapper.class);
    private final ParentAccountLifecycleMapper lifecycleMapper = mock(ParentAccountLifecycleMapper.class);
    private final ParentSmsVerificationService smsService = mock(ParentSmsVerificationService.class);
    private final ParentSmsCodeHasher smsCodeHasher = mock(ParentSmsCodeHasher.class);
    private final DeviceSessionMapper sessionMapper = mock(DeviceSessionMapper.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private ParentMobileManualRecoveryService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ParentMobileManualRecoveryService(
                recoveryMapper, userMapper, userRoleMapper, studentMapper, relationshipMapper,
                lifecycleMapper, smsService, smsCodeHasher, sessionMapper,
                featureAccessService, idGenerator, clock);
    }

    @Test
    void listsOnlyScopedCandidatesWithMaskedMobile() {
        when(recoveryMapper.findCandidatesByOrganizationAdministrator(OPERATOR_ID))
                .thenReturn(List.of(candidateRow()));

        List<ParentMobileManualRecoveryCandidate> candidates = service.listCandidates(operator());

        assertThat(candidates).containsExactly(new ParentMobileManualRecoveryCandidate(
                STUDENT_ID, "学生甲", ORGANIZATION_ID, "第一学校", PARENT_ID,
                "家长甲", "138****8000", ParentRelationshipRole.PRIMARY_GUARDIAN));
    }

    @Test
    void rejectsCodeIssueWhenCandidateIsOutsideOrganizationScope() {
        when(recoveryMapper.findAccessibleCandidate(OPERATOR_ID, STUDENT_ID, PARENT_ID))
                .thenReturn(null);

        assertThatThrownBy(() -> service.issueCode(operator(),
                new IssueParentMobileManualRecoveryCodeCommand(
                        STUDENT_ID, PARENT_ID, NEW_MOBILE, "source-digest")))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(smsService, never()).issue(any(), any(), any(), any());
    }

    @Test
    void rejectsRecoveryWhileParentHasActiveCancellation() {
        when(userMapper.findByIdForUpdate(PARENT_ID)).thenReturn(parent());
        when(userRoleMapper.hasRoleCode(PARENT_ID, "PARENT")).thenReturn(true);
        when(lifecycleMapper.findActiveCancellation(PARENT_ID)).thenReturn(new ParentAccountCancellationRecord(
                AUDIT_ID, PARENT_ID, ParentAccountCancellationStatus.COOLING_OFF,
                "ACTIVE", NOW.minusDays(1), NOW.plusDays(6), null, null));

        assertThatThrownBy(() -> service.recover(operator(), recoveryCommand()))
                .isInstanceOf(ParentAccountCancellationConflictException.class);

        verify(smsService, never()).verifyAndConsume(any(), any(), any(), any());
        verify(recoveryMapper, never()).insert(any());
    }

    @Test
    void rejectsDefaultLoginNameCollisionBeforeConsumingCode() {
        when(userMapper.findByIdForUpdate(PARENT_ID)).thenReturn(parent());
        when(userRoleMapper.hasRoleCode(PARENT_ID, "PARENT")).thenReturn(true);
        when(studentMapper.findByIdForUpdate(STUDENT_ID)).thenReturn(student());
        when(relationshipMapper.findByParentAndStudentForUpdate(PARENT_ID, STUDENT_ID))
                .thenReturn(relationship());
        when(recoveryMapper.findAccessibleOrganizationId(OPERATOR_ID, STUDENT_ID))
                .thenReturn(ORGANIZATION_ID);
        when(userMapper.existsByUsername(NEW_MOBILE)).thenReturn(true);

        assertThatThrownBy(() -> service.recover(operator(), recoveryCommand()))
                .isInstanceOf(ParentMobileChangeConflictException.class);

        verify(smsService, never()).verifyAndConsume(any(), any(), any(), any());
        verify(userMapper, never()).updateMobileIfExpected(any(), any(), any());
    }

    @Test
    void changesMobileWritesDigestAuditAndRevokesSessionsAtomically() {
        when(userMapper.findByIdForUpdate(PARENT_ID)).thenReturn(parent());
        when(userRoleMapper.hasRoleCode(PARENT_ID, "PARENT")).thenReturn(true);
        when(studentMapper.findByIdForUpdate(STUDENT_ID)).thenReturn(student());
        when(relationshipMapper.findByParentAndStudentForUpdate(PARENT_ID, STUDENT_ID))
                .thenReturn(relationship());
        when(recoveryMapper.findAccessibleOrganizationId(OPERATOR_ID, STUDENT_ID))
                .thenReturn(ORGANIZATION_ID);
        when(userMapper.existsByMobile(NEW_MOBILE)).thenReturn(false);
        when(userMapper.updateMobileIfExpected(PARENT_ID, OLD_MOBILE, NEW_MOBILE)).thenReturn(1);
        when(smsCodeHasher.mobileDigest(OLD_MOBILE)).thenReturn("old-digest");
        when(smsCodeHasher.mobileDigest(NEW_MOBILE)).thenReturn("new-digest");
        when(idGenerator.nextId()).thenReturn(AUDIT_ID);
        when(recoveryMapper.insert(any())).thenReturn(1);

        service.recover(operator(), recoveryCommand());

        verify(smsService).verifyAndConsume(
                NEW_MOBILE, ParentSmsPurpose.MANUAL_MOBILE_RECOVERY_NEW,
                AuthClientType.WEB, "123456");
        verify(userMapper).updateMobileIfExpected(PARENT_ID, OLD_MOBILE, NEW_MOBILE);
        verify(recoveryMapper).insert(new ParentMobileManualRecoveryRecord(
                AUDIT_ID, PARENT_ID, STUDENT_ID, ORGANIZATION_ID, OPERATOR_ID,
                "old-digest", "new-digest", "家长到校核验", AuthClientType.WEB, NOW));
        verify(sessionMapper).revokeAllActiveByUserId(PARENT_ID, NOW);
    }

    private AuthenticatedUser operator() {
        return new AuthenticatedUser(OPERATOR_ID, 1L, "school_admin", "学校管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));
    }

    private ParentMobileManualRecoveryCandidateRow candidateRow() {
        return new ParentMobileManualRecoveryCandidateRow(
                STUDENT_ID, "学生甲", ORGANIZATION_ID, "第一学校", PARENT_ID,
                "家长甲", OLD_MOBILE, ParentRelationshipRole.PRIMARY_GUARDIAN);
    }

    private ParentMobileManualRecoveryCommand recoveryCommand() {
        return new ParentMobileManualRecoveryCommand(
                STUDENT_ID, PARENT_ID, NEW_MOBILE, "123456",
                "家长到校核验", "已完成线下身份核验");
    }

    private User parent() {
        return new User(PARENT_ID, OLD_MOBILE, "家长甲", OLD_MOBILE, "password-hash",
                UserType.FAMILY, UserStatus.ENABLED, null, null);
    }

    private Student student() {
        return new Student(STUDENT_ID, "学生甲", null, null, StudentStatus.ENABLED, null, null);
    }

    private ParentRelationship relationship() {
        return new ParentRelationship(AUDIT_ID, PARENT_ID, STUDENT_ID,
                ParentRelationshipRole.PRIMARY_GUARDIAN, "ACTIVE", "PRIMARY", NOW.minusYears(1), null);
    }
}
