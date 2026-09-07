package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.infrastructure.persistence.IamAuditExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.IamAuditExportRow;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IamChangeAuditExportAdapterTest {
    private static final long UPPER_BOUND = 1874244142494646999L;

    @Test
    void appliesCombinedFilterCursorAndUserNameMasking() {
        IamAuditExportMapper mapper = mock(IamAuditExportMapper.class);
        IamChangeAuditExportAdapter adapter = new IamChangeAuditExportAdapter(mapper);
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 9, 3, 23, 59, 59);
        ExportRequestDefinition request = new ExportRequestDefinition(
                1874244142494646930L, null, startedAt, endedAt,
                IamChangeAuditEventType.USER_STATUS_CHANGE);

        when(mapper.findUpperBound(startedAt, endedAt, IamChangeAuditEventType.USER_STATUS_CHANGE))
                .thenReturn(UPPER_BOUND);
        when(mapper.count(startedAt, endedAt, IamChangeAuditEventType.USER_STATUS_CHANGE, UPPER_BOUND))
                .thenReturn(1L);
        when(mapper.findAfter(startedAt, endedAt, IamChangeAuditEventType.USER_STATUS_CHANGE,
                UPPER_BOUND, 1874244142494646931L, 3))
                .thenReturn(List.of(new IamAuditExportRow(
                        1874244142494646932L, LocalDateTime.of(2026, 9, 2, 9, 8, 7),
                        IamChangeAuditEventType.USER_STATUS_CHANGE, IamChangeTargetType.USER,
                        1874244142494646933L, "李同学", "王管理员", "ENABLED", "DISABLED")));

        assertThat(adapter.captureUpperBound(request)).isEqualTo(UPPER_BOUND);
        assertThat(adapter.count(request, UPPER_BOUND)).isEqualTo(1L);
        ExportDataPage page = adapter.fetchAfter(
                request, UPPER_BOUND, 1874244142494646931L, 2);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isEqualTo(1874244142494646932L);
        assertThat(page.rows()).singleElement().satisfies(values -> assertThat(values)
                .containsEntry("OCCURRED_AT", "2026-09-02 09:08:07")
                .containsEntry("EVENT_TYPE", "用户状态变更")
                .containsEntry("TARGET_TYPE", "用户")
                .containsEntry("TARGET_ID", "1874244142494646933")
                .containsEntry("TARGET_NAME", "李*")
                .containsEntry("OPERATOR_NAME", "王*")
                .containsEntry("BEFORE_SUMMARY", "ENABLED")
                .containsEntry("AFTER_SUMMARY", "DISABLED")
                .containsEntry("RESULT", "成功")
                .hasSize(9));
        assertThat(adapter.columns()).extracting(column -> column.code())
                .containsExactly("OCCURRED_AT", "EVENT_TYPE", "TARGET_TYPE", "TARGET_ID",
                        "TARGET_NAME", "OPERATOR_NAME", "BEFORE_SUMMARY", "AFTER_SUMMARY", "RESULT");
        assertThat(adapter.columns()).allMatch(column -> column.defaultSelected());

        verify(mapper).findAfter(startedAt, endedAt, IamChangeAuditEventType.USER_STATUS_CHANGE,
                UPPER_BOUND, 1874244142494646931L, 3);
    }
}
