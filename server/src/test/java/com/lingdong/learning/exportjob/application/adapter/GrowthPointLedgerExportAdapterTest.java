package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.infrastructure.persistence.GrowthPointExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.GrowthPointExportRow;
import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GrowthPointLedgerExportAdapterTest {
    private static final long STUDENT_ID = 1874244142494646921L;
    private static final long UPPER_BOUND = 1874244142494646999L;

    @Test
    void usesStableCursorBoundaryAndReturnsOnlyControlledMaskedColumns() {
        GrowthPointExportMapper mapper = mock(GrowthPointExportMapper.class);
        GrowthPointLedgerExportAdapter adapter = new GrowthPointLedgerExportAdapter(mapper);
        LocalDateTime startedAt = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 8, 31, 23, 59, 59);
        ExportRequestDefinition request = new ExportRequestDefinition(
                1874244142494646920L, STUDENT_ID, startedAt, endedAt, null);

        when(mapper.findUpperBound(STUDENT_ID, startedAt, endedAt)).thenReturn(UPPER_BOUND);
        when(mapper.count(STUDENT_ID, startedAt, endedAt, UPPER_BOUND)).thenReturn(3L);
        when(mapper.findAfter(STUDENT_ID, startedAt, endedAt, UPPER_BOUND, 0L, 3))
                .thenReturn(List.of(
                        row(1874244142494646922L, "小灵", "王老师"),
                        row(1874244142494646923L, "小灵", "王老师"),
                        row(1874244142494646924L, "小灵", "王老师")));

        assertThat(adapter.captureUpperBound(request)).isEqualTo(UPPER_BOUND);
        assertThat(adapter.count(request, UPPER_BOUND)).isEqualTo(3L);
        ExportDataPage page = adapter.fetchAfter(request, UPPER_BOUND, 0L, 2);

        assertThat(page.hasMore()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(1874244142494646923L);
        assertThat(page.rows()).hasSize(2);
        assertThat(page.rows().get(0))
                .containsEntry("OCCURRED_AT", "2026-08-16 10:11:12")
                .containsEntry("STUDENT_NAME", "小*")
                .containsEntry("CHANGE_TYPE", "任务奖励")
                .containsEntry("AMOUNT", 20L)
                .containsEntry("AVAILABLE_DELTA", 20L)
                .containsEntry("SOURCE_TYPE", "教师")
                .containsEntry("SOURCE_ORGANIZATION", "灵动学校")
                .containsEntry("TASK_TITLE", "晨读")
                .containsEntry("REVIEWER_NAME", "王*")
                .containsEntry("REMARK", "按时完成");
        assertThat(page.rows().get(0)).hasSize(10);
        assertThat(adapter.columns()).extracting(column -> column.code())
                .containsExactly("OCCURRED_AT", "STUDENT_NAME", "CHANGE_TYPE", "AMOUNT",
                        "AVAILABLE_DELTA", "SOURCE_TYPE", "SOURCE_ORGANIZATION", "TASK_TITLE",
                        "REVIEWER_NAME", "REMARK");
        assertThat(adapter.columns()).filteredOn(column -> column.defaultSelected())
                .extracting(column -> column.code())
                .containsExactly("OCCURRED_AT", "STUDENT_NAME", "CHANGE_TYPE", "AMOUNT",
                        "AVAILABLE_DELTA", "SOURCE_TYPE");

        verify(mapper).findAfter(STUDENT_ID, startedAt, endedAt, UPPER_BOUND, 0L, 3);
    }

    private GrowthPointExportRow row(long id, String studentName, String reviewerName) {
        return new GrowthPointExportRow(
                id, LocalDateTime.of(2026, 8, 16, 10, 11, 12), studentName,
                GrowthPointChangeType.TASK_REWARD, 20L, 20L, LearningTaskSourceType.TEACHER,
                "灵动学校", "晨读", reviewerName, "按时完成");
    }
}
