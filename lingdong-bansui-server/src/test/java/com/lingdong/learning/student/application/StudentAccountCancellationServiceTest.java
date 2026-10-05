package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.StudentQrTicketMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.student.domain.ParentRelationshipRole;
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

class StudentAccountCancellationServiceTest {
    private static final long OPERATOR_ID = 1874244142494647001L;
    private static final long STUDENT_ID = 1874244142494647002L;
    private static final long STUDENT_USER_ID = 1874244142494647003L;
    private static final long ORGANIZATION_ID = 1874244142494647004L;
    private static final long AUDIT_ID = 1874244142494647005L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 11, 0);

    private final StudentAccountCancellationMapper cancellationMapper = mock(StudentAccountCancellationMapper.class);
    private final StudentMapper studentMapper = mock(StudentMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final StudentOrganizationMapper organizationMapper = mock(StudentOrganizationMapper.class);
    private final StudentCredentialMapper credentialMapper = mock(StudentCredentialMapper.class);
    private final StudentQrTicketMapper qrTicketMapper = mock(StudentQrTicketMapper.class);
    private final DeviceSessionMapper sessionMapper = mock(DeviceSessionMapper.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final StudentManagementAccessService managementAccess = mock(StudentManagementAccessService.class);
    private final com.lingdong.learning.datascope.application.OrganizationDataScope scope = com.lingdong.learning.datascope.application.OrganizationDataScope.all(false);
    private StudentAccountCancellationService service;

    @BeforeEach
    void setUp() {
        when(managementAccess.webAllowed(any(), org.mockito.ArgumentMatchers.eq("STUDENT_ACCOUNT_CANCELLATION_MANAGE"))).thenReturn(true);
        when(managementAccess.scope(any())).thenReturn(scope);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new StudentAccountCancellationService(
                cancellationMapper, studentMapper, userMapper, userRoleMapper,
                parentStudentMapper, organizationMapper, credentialMapper, qrTicketMapper,
                sessionMapper, featureAccessService, idGenerator, clock, managementAccess);
    }

    @Test
    void listsOnlyCandidatesFromLatestInactiveEnrollmentScope() {
        when(cancellationMapper.findCandidatesByOrganizationScope(scope))
                .thenReturn(List.of(candidateRow()));

        assertThat(service.listCandidates(operator()))
                .containsExactly(new StudentAccountCancellationCandidate(
                        STUDENT_ID, "待注销学生", "20260001",
                        ORGANIZATION_ID, "原学校"));
    }

    @Test
    void rejectsWhenStudentIsOutsideHistoricalOrganizationScope() {
        when(studentMapper.findByIdForUpdate(STUDENT_ID)).thenReturn(student());
        when(userMapper.findByIdForUpdate(STUDENT_USER_ID)).thenReturn(studentUser());
        when(userRoleMapper.hasRoleCode(STUDENT_USER_ID, "STUDENT")).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(operator(), command()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(studentMapper, never()).anonymizeCancelledStudent(any());
    }

    @Test
    void rejectsWhenAnyActiveRelationshipRemains() {
        when(studentMapper.findByIdForUpdate(STUDENT_ID)).thenReturn(student());
        when(userMapper.findByIdForUpdate(STUDENT_USER_ID)).thenReturn(studentUser());
        when(userRoleMapper.hasRoleCode(STUDENT_USER_ID, "STUDENT")).thenReturn(true);
        when(organizationMapper.findActiveOrganizationIdsForUpdate(STUDENT_ID))
                .thenReturn(List.of(ORGANIZATION_ID));

        assertThatThrownBy(() -> service.cancel(operator(), command()))
                .isInstanceOf(StudentAccountCancellationConflictException.class);

        verify(cancellationMapper, never()).insert(any());
    }

    @Test
    void rejectsWhenActiveParentRelationshipRemains() {
        when(studentMapper.findByIdForUpdate(STUDENT_ID)).thenReturn(student());
        when(userMapper.findByIdForUpdate(STUDENT_USER_ID)).thenReturn(studentUser());
        when(userRoleMapper.hasRoleCode(STUDENT_USER_ID, "STUDENT")).thenReturn(true);
        when(organizationMapper.findActiveOrganizationIdsForUpdate(STUDENT_ID)).thenReturn(List.of());
        when(parentStudentMapper.findActiveByStudentIdForUpdate(STUDENT_ID)).thenReturn(List.of(
                new ParentRelationship(
                        1874244142494647006L, 1874244142494647007L, STUDENT_ID,
                        ParentRelationshipRole.PRIMARY_GUARDIAN, "ACTIVE", "PRIMARY",
                        NOW.minusYears(1), null)));

        assertThatThrownBy(() -> service.cancel(operator(), command()))
                .isInstanceOf(StudentAccountCancellationConflictException.class);

        verify(cancellationMapper, never()).insert(any());
    }

    @Test
    void rejectsIncorrectIrreversibleConfirmationBeforeLockingStudent() {
        StudentAccountCancellationCommand invalidCommand = new StudentAccountCancellationCommand(
                STUDENT_ID, "学生已完成退学且家长关系已解除", "注销学生账号");

        assertThatThrownBy(() -> service.cancel(operator(), invalidCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("请输入确认注销学生账号");

        verify(studentMapper, never()).findByIdForUpdate(any());
    }

    @Test
    void anonymizesIdentityRevokesCredentialsAndWritesAuditAtomically() {
        when(studentMapper.findByIdForUpdate(STUDENT_ID)).thenReturn(student());
        when(userMapper.findByIdForUpdate(STUDENT_USER_ID)).thenReturn(studentUser());
        when(userRoleMapper.hasRoleCode(STUDENT_USER_ID, "STUDENT")).thenReturn(true);
        when(organizationMapper.findActiveOrganizationIdsForUpdate(STUDENT_ID)).thenReturn(List.of());
        when(parentStudentMapper.findActiveByStudentIdForUpdate(STUDENT_ID)).thenReturn(List.of());
        when(cancellationMapper.findAccessibleLatestInactiveEnrollmentOrganizationIdByScope(
                scope, STUDENT_ID)).thenReturn(ORGANIZATION_ID);
        when(studentMapper.anonymizeCancelledStudent(STUDENT_ID)).thenReturn(1);
        when(userMapper.anonymizeCancelledStudent(
                STUDENT_USER_ID, "cancelled_student_" + STUDENT_ID)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(AUDIT_ID);
        when(cancellationMapper.insert(any())).thenReturn(1);

        service.cancel(operator(), command());

        verify(credentialMapper).deleteByStudentUserId(STUDENT_USER_ID);
        verify(qrTicketMapper).revokeActiveByStudentId(STUDENT_ID);
        verify(sessionMapper).revokeAllActiveByUserId(STUDENT_USER_ID, NOW);
        verify(cancellationMapper).insert(new StudentAccountCancellationRecord(
                AUDIT_ID, STUDENT_ID, STUDENT_USER_ID, ORGANIZATION_ID, OPERATOR_ID,
                "学生已完成退学且家长关系已解除", AuthClientType.WEB, NOW));
    }

    private AuthenticatedUser operator() {
        return new AuthenticatedUser(OPERATOR_ID, 1L, "school_admin", "学校管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));
    }

    private StudentAccountCancellationCommand command() {
        return new StudentAccountCancellationCommand(
                STUDENT_ID, "学生已完成退学且家长关系已解除", "确认注销学生账号");
    }

    private StudentAccountCancellationCandidateRow candidateRow() {
        return new StudentAccountCancellationCandidateRow(
                STUDENT_ID, "待注销学生", STUDENT_USER_ID, "20260001",
                ORGANIZATION_ID, "原学校");
    }

    private Student student() {
        return new Student(STUDENT_ID, "待注销学生", "GRADE_4", STUDENT_USER_ID,
                StudentStatus.ENABLED, null, null);
    }

    private User studentUser() {
        return new User(STUDENT_USER_ID, "20260001", "待注销学生", null,
                "password-hash", UserType.STUDENT, UserStatus.ENABLED, null, null);
    }
}
