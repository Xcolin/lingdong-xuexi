package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobClaimServiceTest {
    @Test
    void onlyConditionalUpdateWinnerCanClaimAndRecordEvent() {
        ExportJobMapper mapper = mock(ExportJobMapper.class);
        ExportJobEventService eventService = mock(ExportJobEventService.class);
        ExportJobClaimService service = new ExportJobClaimService(mapper, eventService);
        ExportJobRecord queued = job(ExportJobStatus.QUEUED, 0L);
        ExportJobRecord exporting = job(ExportJobStatus.EXPORTING, 1L);
        when(mapper.findById(queued.id())).thenReturn(queued, exporting);
        when(mapper.claim(queued.id(), 0L)).thenReturn(1);

        ExportJobRecord claimed = service.claim(queued.id(), 0L);

        assertThat(claimed.status()).isEqualTo(ExportJobStatus.EXPORTING);
        assertThat(claimed.versionNo()).isEqualTo(1L);
        verify(eventService).record(queued.id(), ExportJobEventType.CLAIMED, null, "系统已领取导出作业");
    }

    @Test
    void returnsNullWithoutEventWhenAnotherInstanceWins() {
        ExportJobMapper mapper = mock(ExportJobMapper.class);
        ExportJobEventService eventService = mock(ExportJobEventService.class);
        ExportJobClaimService service = new ExportJobClaimService(mapper, eventService);
        when(mapper.findById(1L)).thenReturn(job(ExportJobStatus.QUEUED, 0L));
        when(mapper.claim(1L, 0L)).thenReturn(0);

        assertThat(service.claim(1L, 0L)).isNull();

        verify(eventService, never()).record(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private ExportJobRecord job(ExportJobStatus status, long version) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 13, 0);
        return new ExportJobRecord(
                1L, "EXP-1", ExportJobType.GROWTH_POINT_LEDGER, 2L, "模板", "V1",
                3L, 4L, null, "{}", "[]", "{}", "{}", "原因", false,
                status, version, null, 0L, 0L, null, null, "0".repeat(64),
                now, null, now, status == ExportJobStatus.EXPORTING ? now : null,
                null, now, now);
    }
}
