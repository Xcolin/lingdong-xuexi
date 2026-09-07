package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.auth.infrastructure.security.ParentSmsCodeHasher;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentAccountLifecycleServiceTest {
    private static final long USER_ID = 1874244142494646701L;
    private static final String OLD_MOBILE = "13800138000";
    private static final String NEW_MOBILE = "13900139000";
    private static final String TICKET = "opaque-mobile-change-ticket";

    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final ParentAuthenticationMapper parentAuthenticationMapper = mock(ParentAuthenticationMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final ParentSmsVerificationService smsService = mock(ParentSmsVerificationService.class);
    private final ParentMobileChangeTicketService ticketService = mock(ParentMobileChangeTicketService.class);
    private final ParentSmsCodeHasher hasher = mock(ParentSmsCodeHasher.class);
    private final ParentAccountLifecycleMapper lifecycleMapper = mock(ParentAccountLifecycleMapper.class);
    private final DeviceSessionMapper sessionMapper = mock(DeviceSessionMapper.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final IdGenerator idGenerator = () -> 1874244142494646702L;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-10T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private ParentAccountLifecycleService service;

    @BeforeEach
    void setUp() {
        when(userMapper.findById(USER_ID)).thenReturn(parent(OLD_MOBILE));
        when(userMapper.findByIdForUpdate(USER_ID)).thenReturn(parent(OLD_MOBILE));
        when(userRoleMapper.hasRoleCode(USER_ID, "PARENT")).thenReturn(true);
        when(parentAuthenticationMapper.findProfileByUserId(USER_ID)).thenReturn(
                new ParentProfile(11L, USER_ID, ParentOnboardingStatus.COMPLETED, null, null));
        when(hasher.mobileDigest(OLD_MOBILE)).thenReturn("old-mobile-digest");
        when(hasher.mobileDigest(NEW_MOBILE)).thenReturn("new-mobile-digest");
        service = new ParentAccountLifecycleService(
                userMapper, userRoleMapper, parentAuthenticationMapper, parentStudentMapper,
                smsService, ticketService, hasher, lifecycleMapper, sessionMapper,
                featureAccessService, idGenerator, clock);
    }

    @Test
    void verifiesCurrentMobileAndIssuesBoundTicket() {
        when(ticketService.issue(USER_ID, AuthClientType.MINIAPP, "old-mobile-digest"))
                .thenReturn(TICKET);

        String result = service.verifyCurrentMobile(
                USER_ID, AuthClientType.MINIAPP, "384291");

        assertThat(result).isEqualTo(TICKET);
        verify(smsService).verifyAndConsume(
                OLD_MOBILE, ParentSmsPurpose.CHANGE_MOBILE_CURRENT, AuthClientType.MINIAPP, "384291");
    }

    @Test
    void changesMobileWritesDigestAuditAndRevokesAllSessions() {
        when(userMapper.findByMobileForUpdate(NEW_MOBILE)).thenReturn(null);
        when(userMapper.existsByUsername(NEW_MOBILE)).thenReturn(false);
        when(userMapper.updateMobileIfExpected(USER_ID, OLD_MOBILE, NEW_MOBILE)).thenReturn(1);
        when(lifecycleMapper.insertMobileChange(any(ParentMobileChangeRecord.class))).thenReturn(1);
        when(ticketService.consume(
                TICKET, USER_ID, AuthClientType.WEB, "old-mobile-digest"))
                .thenReturn(new ParentMobileChangeTicket(USER_ID, AuthClientType.WEB, "old-mobile-digest"));

        service.changeMobile(USER_ID, AuthClientType.WEB, TICKET, NEW_MOBILE, "593827");

        verify(smsService).verifyAndConsume(
                NEW_MOBILE, ParentSmsPurpose.CHANGE_MOBILE_NEW, AuthClientType.WEB, "593827");
        verify(userMapper).updateMobileIfExpected(USER_ID, OLD_MOBILE, NEW_MOBILE);
        verify(lifecycleMapper).insertMobileChange(eq(new ParentMobileChangeRecord(
                1874244142494646702L, USER_ID, "old-mobile-digest", "new-mobile-digest",
                AuthClientType.WEB, java.time.LocalDateTime.now(clock))));
        verify(sessionMapper).revokeAllActiveByUserId(USER_ID, java.time.LocalDateTime.now(clock));
    }

    @Test
    void rejectsOccupiedNewMobileBeforeConsumingVerificationFactors() {
        when(userMapper.findByMobileForUpdate(NEW_MOBILE)).thenReturn(
                new User(99L, NEW_MOBILE, "其他家长", NEW_MOBILE, null,
                        UserType.FAMILY, UserStatus.ENABLED, null, null));

        assertThatThrownBy(() -> service.changeMobile(
                USER_ID, AuthClientType.WEB, TICKET, NEW_MOBILE, "593827"))
                .isInstanceOf(ParentMobileChangeConflictException.class);

        verify(ticketService, never()).consume(any(), any(), any(), any());
        verify(smsService, never()).verifyAndConsume(
                any(), eq(ParentSmsPurpose.CHANGE_MOBILE_NEW), any(), any());
        verify(userMapper, never()).updateMobileIfExpected(any(), any(), any());
    }

    @Test
    void rejectsCancellationWhileAnyStudentRelationshipIsActive() {
        when(parentStudentMapper.countActiveStudentsByParent(USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.requestCancellation(
                USER_ID, AuthClientType.MINIAPP, "384291", "确认注销"))
                .isInstanceOf(ParentAccountCancellationConflictException.class)
                .hasMessageContaining("学生关系");

        verify(smsService, never()).verifyAndConsume(
                any(), eq(ParentSmsPurpose.ACCOUNT_CANCELLATION), any(), any());
    }

    @Test
    void createsSevenDayCoolingOffRequestAndReturnsExistingRequestIdempotently() {
        when(parentStudentMapper.countActiveStudentsByParent(USER_ID)).thenReturn(0L);
        when(lifecycleMapper.findActiveCancellationForUpdate(USER_ID)).thenReturn(null);
        when(lifecycleMapper.insertCancellation(any(ParentAccountCancellationRecord.class))).thenReturn(1);

        ParentAccountCancellationRecord created = service.requestCancellation(
                USER_ID, AuthClientType.WEB, "384291", "确认注销");

        assertThat(created.status()).isEqualTo(ParentAccountCancellationStatus.COOLING_OFF);
        assertThat(created.coolingEndsAt()).isEqualTo(created.requestedAt().plusDays(7));
        verify(smsService).verifyAndConsume(
                OLD_MOBILE, ParentSmsPurpose.ACCOUNT_CANCELLATION, AuthClientType.WEB, "384291");

        when(lifecycleMapper.findActiveCancellationForUpdate(USER_ID)).thenReturn(created);
        assertThat(service.requestCancellation(
                USER_ID, AuthClientType.WEB, "unused", "确认注销")).isEqualTo(created);
        verify(lifecycleMapper).insertCancellation(any(ParentAccountCancellationRecord.class));
    }

    @Test
    void calculatesReadyStatusAfterCoolingPeriodWithoutFinalizingAccount() {
        LocalDateTime requestedAt = LocalDateTime.now(clock).minusDays(8);
        ParentAccountCancellationRecord record = new ParentAccountCancellationRecord(
                1874244142494646702L, USER_ID, ParentAccountCancellationStatus.COOLING_OFF,
                "ACTIVE", requestedAt, requestedAt.plusDays(7), null, null);
        when(parentStudentMapper.countActiveStudentsByParent(USER_ID)).thenReturn(0L);
        when(lifecycleMapper.findActiveCancellation(USER_ID)).thenReturn(record);

        ParentAccountLifecycleState state = service.getState(USER_ID);

        assertThat(state.cancellationStatus()).isEqualTo(ParentAccountCancellationViewStatus.READY_FOR_FINALIZATION);
        assertThat(state.activeStudentRelationshipCount()).isZero();
        verify(userMapper, never()).updateStatus(any(), any());
    }

    @Test
    void revokesCoolingOffRequestButDoesNotChangeUserStatus() {
        LocalDateTime now = LocalDateTime.now(clock);
        ParentAccountCancellationRecord record = new ParentAccountCancellationRecord(
                1874244142494646702L, USER_ID, ParentAccountCancellationStatus.COOLING_OFF,
                "ACTIVE", now, now.plusDays(7), null, null);
        when(lifecycleMapper.findActiveCancellationForUpdate(USER_ID)).thenReturn(record);
        when(lifecycleMapper.revokeCancellation(
                record.id(), "CLOSED:" + record.id(), now)).thenReturn(1);

        service.revokeCancellation(USER_ID);

        verify(lifecycleMapper).revokeCancellation(record.id(), "CLOSED:" + record.id(), now);
        verify(userMapper, never()).updateStatus(any(), any());
    }

    private User parent(String mobile) {
        return new User(USER_ID, OLD_MOBILE, "家长用户", mobile, null,
                UserType.FAMILY, UserStatus.ENABLED, null, null);
    }
}
