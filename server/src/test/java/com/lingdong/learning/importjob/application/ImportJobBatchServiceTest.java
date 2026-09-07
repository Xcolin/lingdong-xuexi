package com.lingdong.learning.importjob.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobBatchServiceTest {
    private ImportJobMapper jobMapper;
    private ImportJobClaimService claimService;
    private ImportJobResultService resultService;
    private FeatureAccessService featureAccessService;
    private ImportJobBatchService batchService;

    @BeforeEach
    void setUp() {
        jobMapper = mock(ImportJobMapper.class);
        claimService = mock(ImportJobClaimService.class);
        resultService = mock(ImportJobResultService.class);
        featureAccessService = mock(FeatureAccessService.class);
        batchService = new ImportJobBatchService(
                jobMapper, claimService, resultService, featureAccessService, 2);
    }

    @Test
    void doesNotScanOrClaimWhenFeatureIsDisabled() {
        when(featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)).thenReturn(false);

        assertThat(batchService.processQueuedJobs()).isZero();

        verify(jobMapper, never()).findQueued(2);
        verify(claimService, never()).claim(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void continuesWithLaterJobsAfterOneJobFails() {
        ImportJobRecord firstQueued = job(1L, ImportJobStatus.QUEUED, 0L);
        ImportJobRecord secondQueued = job(2L, ImportJobStatus.QUEUED, 0L);
        ImportJobRecord firstClaimed = job(1L, ImportJobStatus.VALIDATING, 1L);
        ImportJobRecord secondClaimed = job(2L, ImportJobStatus.VALIDATING, 1L);
        enableRequiredFeatures();
        when(jobMapper.findQueued(2)).thenReturn(List.of(firstQueued, secondQueued));
        when(claimService.claim(1L, 0L)).thenReturn(firstClaimed);
        when(claimService.claim(2L, 0L)).thenReturn(secondClaimed);
        doThrow(new IllegalStateException("模拟单作业失败"))
                .when(resultService).process(firstClaimed);

        assertThat(batchService.processQueuedJobs()).isEqualTo(1);

        verify(resultService).markSystemFailed(
                firstClaimed, "IMPORT_VALIDATION_SYSTEM_ERROR", "导入校验处理失败");
        verify(resultService).process(secondClaimed);
    }

    @Test
    void skipsCandidateLostToAnotherInstance() {
        ImportJobRecord queued = job(1L, ImportJobStatus.QUEUED, 0L);
        enableRequiredFeatures();
        when(jobMapper.findQueued(2)).thenReturn(List.of(queued));
        when(claimService.claim(1L, 0L)).thenReturn(null);

        assertThat(batchService.processQueuedJobs()).isZero();

        verify(resultService, never()).process(org.mockito.ArgumentMatchers.any());
    }

    private void enableRequiredFeatures() {
        when(featureAccessService.isEnabled("DATA_IMPORT_VALIDATION", null)).thenReturn(true);
        when(featureAccessService.isEnabled("ATTACHMENT_SERVICE", null)).thenReturn(true);
        when(featureAccessService.isEnabled("IMPORT_EXPORT_TEMPLATE_MANAGEMENT", null))
                .thenReturn(true);
    }

    private ImportJobRecord job(Long id, ImportJobStatus status, Long versionNo) {
        LocalDateTime now = LocalDateTime.now();
        return new ImportJobRecord(
                id, "IMP-" + id, 2L, "V1", "模板", "[]", 3L, null,
                4L, null, status, versionNo, null, null, 0, 0, 0, 0,
                now, status == ImportJobStatus.VALIDATING ? now : null,
                null, now, now
        );
    }
}
