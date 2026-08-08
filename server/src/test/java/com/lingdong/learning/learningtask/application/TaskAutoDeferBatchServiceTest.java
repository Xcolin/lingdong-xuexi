package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskAutoDeferBatchServiceTest {
    private final TaskDeferMapper deferMapper = mock(TaskDeferMapper.class);
    private final TaskDeferTransactionService transactionService = mock(TaskDeferTransactionService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-07T16:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void processesYesterdayCandidatesWithIndependentFailureIsolation() {
        TaskAutoDeferBatchService service = new TaskAutoDeferBatchService(
                deferMapper, transactionService, featureAccessService, clock, 2);
        LocalDate sourceDate = LocalDate.of(2026, 8, 7);
        LocalDate targetDate = LocalDate.of(2026, 8, 8);
        when(featureAccessService.isEnabled("LEARNING_TASK_MANAGEMENT", null)).thenReturn(true);
        when(deferMapper.findAutomaticCandidateIdsAfter(0L, sourceDate, 2))
                .thenReturn(List.of(11L, 22L));
        when(deferMapper.findAutomaticCandidateIdsAfter(22L, sourceDate, 2))
                .thenReturn(List.of());
        when(transactionService.deferAutomatically(11L, targetDate))
                .thenThrow(new IllegalStateException("模拟单条失败"));
        when(transactionService.deferAutomatically(22L, targetDate))
                .thenReturn(new TaskDeferResult(22L, 33L, "PENDING_CLAIM",
                        targetDate, com.lingdong.learning.learningtask.domain.TaskDeferType.AUTO, true));

        assertThat(service.processYesterdayAssignments()).isEqualTo(1);

        verify(transactionService).deferAutomatically(22L, targetDate);
        verify(deferMapper).findAutomaticCandidateIdsAfter(22L, sourceDate, 2);
    }

    @Test
    void doesNothingWhenFeatureIsDisabled() {
        TaskAutoDeferBatchService service = new TaskAutoDeferBatchService(
                deferMapper, transactionService, featureAccessService, clock, 100);
        when(featureAccessService.isEnabled("LEARNING_TASK_MANAGEMENT", null)).thenReturn(false);

        assertThat(service.processYesterdayAssignments()).isZero();

        verify(deferMapper, never()).findAutomaticCandidateIdsAfter(
                0L, LocalDate.of(2026, 8, 7), 100);
    }
}
