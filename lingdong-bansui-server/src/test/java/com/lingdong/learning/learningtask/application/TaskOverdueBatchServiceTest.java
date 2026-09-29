package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOverdueBatchServiceTest {
    private final LearningTaskAssignmentMapper assignmentMapper = mock(LearningTaskAssignmentMapper.class);
    private final TaskOverdueTransactionService transactionService = mock(TaskOverdueTransactionService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-08T15:59:59Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void isolatesFailuresAndAdvancesTheAssignmentCursor() {
        TaskOverdueBatchService service = new TaskOverdueBatchService(
                assignmentMapper, transactionService, featureAccessService, clock, 2);
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 8, 23, 59, 59);
        when(featureAccessService.isEnabled("LEARNING_TASK_MANAGEMENT", null)).thenReturn(true);
        when(assignmentMapper.findOverdueIdsAfter(0L, cutoff, 2)).thenReturn(List.of(11L, 22L));
        when(assignmentMapper.findOverdueIdsAfter(22L, cutoff, 2)).thenReturn(List.of());
        when(transactionService.markNeedsImprovement(11L, cutoff))
                .thenThrow(new IllegalStateException("模拟单条失败"));
        when(transactionService.markNeedsImprovement(22L, cutoff)).thenReturn(true);

        assertThat(service.processOverdueAssignments()).isEqualTo(1);

        verify(transactionService).markNeedsImprovement(22L, cutoff);
        verify(assignmentMapper).findOverdueIdsAfter(22L, cutoff, 2);
    }

    @Test
    void doesNothingWhenFeatureIsDisabled() {
        TaskOverdueBatchService service = new TaskOverdueBatchService(
                assignmentMapper, transactionService, featureAccessService, clock, 100);
        when(featureAccessService.isEnabled("LEARNING_TASK_MANAGEMENT", null)).thenReturn(false);

        assertThat(service.processOverdueAssignments()).isZero();

        verify(assignmentMapper, never()).findOverdueIdsAfter(
                0L, LocalDateTime.of(2026, 8, 8, 23, 59, 59), 100);
    }
}
