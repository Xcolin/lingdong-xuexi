package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentAccountFinalizationProperties;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAccountLifecycleMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
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

class ParentAccountFinalizationBatchServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 14, 9, 0);

    private final ParentAccountLifecycleMapper lifecycleMapper = mock(ParentAccountLifecycleMapper.class);
    private final ParentAccountFinalizationService finalizationService =
            mock(ParentAccountFinalizationService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private ParentAccountFinalizationBatchService batchService;

    @BeforeEach
    void setUp() {
        ParentAccountFinalizationProperties properties = new ParentAccountFinalizationProperties();
        properties.setBatchSize(2);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
        batchService = new ParentAccountFinalizationBatchService(
                lifecycleMapper, finalizationService, featureAccessService, properties, clock);
    }

    @Test
    void processesLimitedCandidatesAndContinuesAfterSingleFailure() {
        when(featureAccessService.isEnabled("PARENT_ACCOUNT_LIFECYCLE", null)).thenReturn(true);
        when(lifecycleMapper.findDueFinalizationIds(NOW, 2)).thenReturn(List.of(11L, 12L));
        when(finalizationService.finalizeCancellation(11L))
                .thenReturn(ParentAccountFinalizationResult.FINALIZED);
        when(finalizationService.finalizeCancellation(12L))
                .thenThrow(new IllegalStateException("模拟单项失败"));

        assertThat(batchService.processDueCancellations()).isEqualTo(1);

        verify(finalizationService).finalizeCancellation(11L);
        verify(finalizationService).finalizeCancellation(12L);
    }

    @Test
    void returnsZeroWithoutInvokingFinalizerWhenNoCandidateIsDue() {
        when(featureAccessService.isEnabled("PARENT_ACCOUNT_LIFECYCLE", null)).thenReturn(true);
        when(lifecycleMapper.findDueFinalizationIds(NOW, 2)).thenReturn(List.of());

        assertThat(batchService.processDueCancellations()).isZero();

        verify(finalizationService, never()).finalizeCancellation(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void pausesBatchWhenFeatureIsDisabled() {
        when(featureAccessService.isEnabled("PARENT_ACCOUNT_LIFECYCLE", null)).thenReturn(false);

        assertThat(batchService.processDueCancellations()).isZero();

        verify(lifecycleMapper, never()).findDueFinalizationIds(NOW, 2);
    }
}
