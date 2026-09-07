package com.lingdong.learning.importjob.application;

import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobClaimServiceTest {
    @Test
    void onlyConditionalUpdateWinnerCanClaimQueuedJob() {
        ImportJobMapper mapper = mock(ImportJobMapper.class);
        ImportJobClaimService service = new ImportJobClaimService(mapper);
        ImportJobRecord queued = job(ImportJobStatus.QUEUED, 0L);
        ImportJobRecord validating = job(ImportJobStatus.VALIDATING, 1L);
        when(mapper.findById(queued.id())).thenReturn(queued, validating);
        when(mapper.claim(queued.id(), 0L)).thenReturn(1);

        ImportJobRecord claimed = service.claim(queued.id(), 0L);

        assertThat(claimed.status()).isEqualTo(ImportJobStatus.VALIDATING);
        assertThat(claimed.versionNo()).isEqualTo(1L);
        verify(mapper).claim(queued.id(), 0L);
    }

    @Test
    void returnsNullWhenAnotherInstanceAlreadyClaimedJob() {
        ImportJobMapper mapper = mock(ImportJobMapper.class);
        ImportJobClaimService service = new ImportJobClaimService(mapper);
        when(mapper.findById(1L)).thenReturn(job(ImportJobStatus.QUEUED, 0L));
        when(mapper.claim(1L, 0L)).thenReturn(0);

        assertThat(service.claim(1L, 0L)).isNull();
    }

    private ImportJobRecord job(ImportJobStatus status, long versionNo) {
        LocalDateTime now = LocalDateTime.now();
        return new ImportJobRecord(
                1L, "IMP-1", 2L, "V1", "模板", "[]", 3L, null,
                4L, null, status, versionNo, null, null, 0, 0, 0, 0,
                now, status == ImportJobStatus.VALIDATING ? now : null,
                null, now, now
        );
    }
}
