package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.growthpoint.domain.GrowthPointAccount;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointDormancyStateRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthPointDormancyServiceTest {
    private GrowthPointLifecycleMapper lifecycleMapper;
    private GrowthPointAccountMapper accountMapper;
    private GrowthPointLedgerMapper ledgerMapper;
    private IdGenerator idGenerator;
    private GrowthPointDormancyService service;

    @BeforeEach
    void setUp() {
        lifecycleMapper = mock(GrowthPointLifecycleMapper.class);
        accountMapper = mock(GrowthPointAccountMapper.class);
        ledgerMapper = mock(GrowthPointLedgerMapper.class);
        idGenerator = mock(IdGenerator.class);
        service = new GrowthPointDormancyService(
                lifecycleMapper, accountMapper, ledgerMapper, idGenerator);
    }

    @Test
    void createsOneReminderAndClearsOnlyAvailablePointsAfterThirtyDays() {
        LocalDateTime baseline = LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
        when(lifecycleMapper.findDormancyStateForUpdate(11L))
                .thenReturn(state(baseline, null, null, 0));
        when(lifecycleMapper.findLatestEffectiveActivityAt(11L)).thenReturn(baseline);
        when(lifecycleMapper.findDormancyNoticeId(11L, baseline)).thenReturn(null);
        when(lifecycleMapper.findPrimaryParentUserId(11L)).thenReturn(99L);
        when(idGenerator.nextId()).thenReturn(1001L, 1002L);
        when(lifecycleMapper.insertDormancyNotice(
                1001L, 11L, 99L, baseline, baseline.plusDays(30), "PENDING", now))
                .thenReturn(1);
        when(lifecycleMapper.markDormancyReminderCreated(11L, now, 0)).thenReturn(1);
        when(accountMapper.findByStudentIdForUpdate(11L))
                .thenReturn(new GrowthPointAccount(11L, 11L, 200L, 50L, 3));
        when(accountMapper.clearAvailablePoints(11L, 50L, 3, now)).thenReturn(1);
        when(ledgerMapper.insert(any())).thenReturn(1);
        when(lifecycleMapper.markDormancyCleared(11L, now, 1)).thenReturn(1);

        GrowthPointDormancyResult result = service.processStudent(11L, now);

        assertThat(result).isEqualTo(new GrowthPointDormancyResult(true, true, 50L, false));
        verify(accountMapper).clearAvailablePoints(11L, 50L, 3, now);
        verify(ledgerMapper).insert(org.mockito.ArgumentMatchers.argThat(ledger ->
                ledger.amount() == 0L && ledger.availableDelta() == -50L
                        && ledger.sourceDormancyNoticeId().equals(1001L)));
    }

    @Test
    void resetsTheCycleWhenANewerEffectiveActivityExists() {
        LocalDateTime baseline = LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime latest = LocalDateTime.of(2026, 7, 20, 9, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
        when(lifecycleMapper.findDormancyStateForUpdate(11L))
                .thenReturn(state(baseline, baseline.plusDays(27), baseline.plusDays(30), 4));
        when(lifecycleMapper.findLatestEffectiveActivityAt(11L)).thenReturn(latest);
        when(lifecycleMapper.resetDormancyCycle(
                11L, latest, latest.plusDays(27), latest.plusDays(30), now, 4)).thenReturn(1);

        GrowthPointDormancyResult result = service.processStudent(11L, now);

        assertThat(result).isEqualTo(new GrowthPointDormancyResult(false, false, 0L, true));
        verify(accountMapper, never()).findByStudentIdForUpdate(any());
        verify(ledgerMapper, never()).insert(any());
    }

    @Test
    void marksAZeroBalanceCycleClearedWithoutWritingAZeroDeltaLedger() {
        LocalDateTime baseline = LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 1, 12, 0);
        when(lifecycleMapper.findDormancyStateForUpdate(11L))
                .thenReturn(state(baseline, LocalDateTime.of(2026, 7, 28, 12, 0), null, 1));
        when(lifecycleMapper.findLatestEffectiveActivityAt(11L)).thenReturn(baseline);
        when(lifecycleMapper.findDormancyNoticeId(11L, baseline)).thenReturn(1001L);
        when(accountMapper.findByStudentIdForUpdate(11L))
                .thenReturn(new GrowthPointAccount(11L, 11L, 200L, 0L, 3));
        when(lifecycleMapper.markDormancyCleared(11L, now, 1)).thenReturn(1);

        GrowthPointDormancyResult result = service.processStudent(11L, now);

        assertThat(result).isEqualTo(new GrowthPointDormancyResult(false, true, 0L, false));
        verify(ledgerMapper, never()).insert(any());
        verify(accountMapper, never()).clearAvailablePoints(eq(11L), eq(0L), eq(3), eq(now));
    }

    private GrowthPointDormancyStateRow state(
            LocalDateTime baseline,
            LocalDateTime reminderCreatedAt,
            LocalDateTime clearedAt,
            int version
    ) {
        return new GrowthPointDormancyStateRow(
                11L, 11L, baseline, baseline.plusDays(27), baseline.plusDays(30),
                reminderCreatedAt, clearedAt, version);
    }
}
