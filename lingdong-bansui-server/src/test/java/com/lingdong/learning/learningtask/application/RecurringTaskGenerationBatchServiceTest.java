package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskRecurrenceMapper;
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

class RecurringTaskGenerationBatchServiceTest {
    private final LearningTaskRecurrenceMapper recurrenceMapper = mock(LearningTaskRecurrenceMapper.class);
    private final RecurringTaskGenerationTransactionService transactionService =
            mock(RecurringTaskGenerationTransactionService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    void continuesAfterOnePlanFailsAndAdvancesTheKeysetCursor() {
        RecurringTaskGenerationBatchService service = new RecurringTaskGenerationBatchService(
                recurrenceMapper, transactionService, featureAccessService, clock, 2);
        LocalDate today = LocalDate.of(2026, 8, 8);
        when(featureAccessService.isEnabled("LEARNING_TASK_MANAGEMENT", null)).thenReturn(true);
        when(recurrenceMapper.findDueIdsAfter(0L, today, 2)).thenReturn(List.of(11L, 22L));
        when(recurrenceMapper.findDueIdsAfter(22L, today, 2)).thenReturn(List.of());
        when(transactionService.generate(11L, today)).thenThrow(new IllegalStateException("模拟计划失败"));
        when(transactionService.generate(22L, today))
                .thenReturn(new RecurringTaskGenerationResult(22L, 1, 2, false));

        assertThat(service.processDuePlans()).isEqualTo(1);

        verify(transactionService).generate(22L, today);
        verify(recurrenceMapper).findDueIdsAfter(22L, today, 2);
    }

    @Test
    void doesNothingWhenFeatureIsDisabled() {
        RecurringTaskGenerationBatchService service = new RecurringTaskGenerationBatchService(
                recurrenceMapper, transactionService, featureAccessService, clock, 100);
        when(featureAccessService.isEnabled("LEARNING_TASK_MANAGEMENT", null)).thenReturn(false);

        assertThat(service.processDuePlans()).isZero();

        verify(recurrenceMapper, never()).findDueIdsAfter(0L, LocalDate.of(2026, 8, 8), 100);
    }
}
