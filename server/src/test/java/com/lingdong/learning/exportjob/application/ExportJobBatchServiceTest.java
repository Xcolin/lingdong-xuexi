package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobBatchServiceTest {
    private ExportJobMapper mapper;
    private ExportJobClaimService claimService;
    private ExportJobExecutionService executionService;
    private FeatureAccessService featureAccessService;
    private ExportJobBatchService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ExportJobMapper.class);
        claimService = mock(ExportJobClaimService.class);
        executionService = mock(ExportJobExecutionService.class);
        featureAccessService = mock(FeatureAccessService.class);
        ExportJobProperties properties = new ExportJobProperties();
        properties.setBatchSize(2);
        service = new ExportJobBatchService(
                mapper, claimService, executionService, featureAccessService, properties);
    }

    @Test
    void doesNotScanWhenAnyRequiredFeatureIsDisabled() {
        when(featureAccessService.isEnabled("DATA_EXPORT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)).thenReturn(false);

        assertThat(service.processQueuedJobs()).isZero();

        verify(mapper, never()).findQueued(2);
    }

    @Test
    void skipsLostClaimAndContinuesAfterOneExecutionFailure() {
        enableFeatures();
        ExportJobRecord first = job(1L, ExportJobStatus.QUEUED, 0L);
        ExportJobRecord lost = job(2L, ExportJobStatus.QUEUED, 0L);
        ExportJobRecord firstClaimed = job(1L, ExportJobStatus.EXPORTING, 1L);
        when(mapper.findQueued(2)).thenReturn(List.of(first, lost));
        when(claimService.claim(1L, 0L)).thenReturn(firstClaimed);
        when(claimService.claim(2L, 0L)).thenReturn(null);
        when(executionService.execute(firstClaimed)).thenReturn(false);

        assertThat(service.processQueuedJobs()).isZero();

        verify(executionService).execute(firstClaimed);
        verify(executionService, never()).execute(lost);
    }

    @Test
    void countsOnlySuccessfullyCompletedJobs() {
        enableFeatures();
        ExportJobRecord queued = job(1L, ExportJobStatus.QUEUED, 0L);
        ExportJobRecord claimed = job(1L, ExportJobStatus.EXPORTING, 1L);
        when(mapper.findQueued(2)).thenReturn(List.of(queued));
        when(claimService.claim(1L, 0L)).thenReturn(claimed);
        when(executionService.execute(claimed)).thenReturn(true);

        assertThat(service.processQueuedJobs()).isEqualTo(1);
    }

    private void enableFeatures() {
        when(featureAccessService.isEnabled("DATA_EXPORT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
    }

    private ExportJobRecord job(Long id, ExportJobStatus status, Long version) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 13, 0);
        return new ExportJobRecord(
                id, "EXP-" + id, ExportJobType.GROWTH_POINT_LEDGER, 2L, "模板", "V1",
                3L, 4L, null, "{}", "[]", "{}", "{}", "原因", false,
                status, version, null, 0L, 0L, null, null, "0".repeat(64),
                now, null, now, status == ExportJobStatus.EXPORTING ? now : null,
                null, now, now);
    }
}
