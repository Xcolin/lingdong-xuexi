package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentAccountFinalizationProperties;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentWechatBindingMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentAccountFinalizationServiceTest {
    private static final long CANCELLATION_ID = 1874244142494646801L;
    private static final long USER_ID = 1874244142494646802L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 9, 0);

    private final ParentAccountLifecycleMapper lifecycleMapper = mock(ParentAccountLifecycleMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final ParentWechatBindingMapper wechatBindingMapper = mock(ParentWechatBindingMapper.class);
    private final DeviceSessionMapper sessionMapper = mock(DeviceSessionMapper.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private ParentAccountFinalizationService service;

    @BeforeEach
    void setUp() {
        ParentAccountFinalizationProperties properties = new ParentAccountFinalizationProperties();
        properties.setRelationshipRetryDelay(Duration.ofDays(1));
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ParentAccountFinalizationService(
                lifecycleMapper, userMapper, userRoleMapper, parentStudentMapper,
                wechatBindingMapper, sessionMapper, featureAccessService, properties, clock);
    }

    @Test
    void skipsCancellationThatIsNotDue() {
        when(lifecycleMapper.findFinalizationCandidateForUpdate(CANCELLATION_ID))
                .thenReturn(candidate(NOW.plusMinutes(1)));

        assertThat(service.finalizeCancellation(CANCELLATION_ID))
                .isEqualTo(ParentAccountFinalizationResult.SKIPPED);

        verify(userMapper, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void rejectsFinalizationWhenFeatureIsDisabled() {
        doThrow(new FeatureDisabledException("PARENT_ACCOUNT_LIFECYCLE"))
                .when(featureAccessService).requireEnabled("PARENT_ACCOUNT_LIFECYCLE", null);

        assertThatThrownBy(() -> service.finalizeCancellation(CANCELLATION_ID))
                .isInstanceOf(FeatureDisabledException.class);

        verify(lifecycleMapper, never()).findFinalizationCandidateForUpdate(anyLong());
    }

    @Test
    void defersFinalizationWhenAStudentRelationshipBecameActiveAgain() {
        when(lifecycleMapper.findFinalizationCandidateForUpdate(CANCELLATION_ID))
                .thenReturn(candidate(NOW.minusMinutes(1)));
        when(userMapper.findByIdForUpdate(USER_ID)).thenReturn(parent());
        when(userRoleMapper.hasRoleCode(USER_ID, "PARENT")).thenReturn(true);
        when(parentStudentMapper.countActiveStudentsByParent(USER_ID)).thenReturn(1L);
        when(lifecycleMapper.deferFinalization(
                CANCELLATION_ID, NOW.plusDays(1), "ACTIVE_RELATIONSHIP")).thenReturn(1);

        assertThat(service.finalizeCancellation(CANCELLATION_ID))
                .isEqualTo(ParentAccountFinalizationResult.DEFERRED_ACTIVE_RELATIONSHIP);

        verify(lifecycleMapper).deferFinalization(
                CANCELLATION_ID, NOW.plusDays(1), "ACTIVE_RELATIONSHIP");
        verify(userMapper, never()).anonymizeCancelledParent(anyLong(), anyString());
        verify(wechatBindingMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    void anonymizesIdentityRevokesSessionsAndFinalizesCancellationAtomically() {
        when(lifecycleMapper.findFinalizationCandidateForUpdate(CANCELLATION_ID))
                .thenReturn(candidate(NOW.minusMinutes(1)));
        when(userMapper.findByIdForUpdate(USER_ID)).thenReturn(parent());
        when(userRoleMapper.hasRoleCode(USER_ID, "PARENT")).thenReturn(true);
        when(parentStudentMapper.countActiveStudentsByParent(USER_ID)).thenReturn(0L);
        when(userMapper.anonymizeCancelledParent(USER_ID, "cancelled_" + USER_ID)).thenReturn(1);
        when(lifecycleMapper.finalizeCancellation(
                CANCELLATION_ID, "CLOSED:" + CANCELLATION_ID, NOW)).thenReturn(1);

        assertThat(service.finalizeCancellation(CANCELLATION_ID))
                .isEqualTo(ParentAccountFinalizationResult.FINALIZED);

        verify(wechatBindingMapper).deleteByUserId(USER_ID);
        verify(userMapper).anonymizeCancelledParent(USER_ID, "cancelled_" + USER_ID);
        verify(sessionMapper).revokeAllActiveByUserId(USER_ID, NOW);
        verify(lifecycleMapper).finalizeCancellation(
                CANCELLATION_ID, "CLOSED:" + CANCELLATION_ID, NOW);
    }

    @Test
    void skipsAlreadyClosedCancellationWithoutTouchingIdentity() {
        when(lifecycleMapper.findFinalizationCandidateForUpdate(CANCELLATION_ID)).thenReturn(null);

        assertThat(service.finalizeCancellation(CANCELLATION_ID))
                .isEqualTo(ParentAccountFinalizationResult.SKIPPED);

        verify(userMapper, never()).anonymizeCancelledParent(anyLong(), anyString());
        verify(sessionMapper, never()).revokeAllActiveByUserId(anyLong(), any());
    }

    private ParentAccountFinalizationCandidate candidate(LocalDateTime nextFinalizeAt) {
        return new ParentAccountFinalizationCandidate(
                CANCELLATION_ID, USER_ID, ParentAccountCancellationStatus.COOLING_OFF,
                "ACTIVE", NOW.minusDays(8), NOW.minusDays(1), nextFinalizeAt, 0);
    }

    private User parent() {
        return new User(USER_ID, "13800138000", "家长用户", "13800138000", "password-hash",
                UserType.FAMILY, UserStatus.ENABLED, null, null);
    }
}
