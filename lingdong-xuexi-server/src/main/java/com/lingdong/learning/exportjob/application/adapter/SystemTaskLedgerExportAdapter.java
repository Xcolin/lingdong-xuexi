package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.SystemTaskLedgerExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.SystemTaskLedgerExportRow;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

/** 以系统任务主键分页输出可见审计事实，不触发审批或执行。 */
@Component
public class SystemTaskLedgerExportAdapter implements ExportDatasetAdapter {
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("TASK_TYPE", "任务类型", true),
            new ExportColumnDefinition("SUBMITTER_ID", "发起人标识", true),
            new ExportColumnDefinition("REVIEWER_ID", "审批人标识", true),
            new ExportColumnDefinition("STATUS", "状态", true),
            new ExportColumnDefinition("CREATED_AT", "创建时间", true),
            new ExportColumnDefinition("REVIEW_COMMENT", "审批意见", true));
    private final SystemTaskLedgerExportMapper mapper;

    public SystemTaskLedgerExportAdapter(SystemTaskLedgerExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.SYSTEM_TASK_LEDGER; }
    @Override public boolean sensitive() { return false; }
    @Override public List<ExportColumnDefinition> columns() { return COLUMNS; }
    @Override public long captureUpperBound(ExportRequestDefinition request) {
        Long value = mapper.findUpperBound(request);
        return value == null ? 0L : value;
    }
    @Override public long count(ExportRequestDefinition request, long upperBound) {
        return mapper.count(request, upperBound);
    }
    @Override public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        if (limit < 1 || limit >= Integer.MAX_VALUE || cursor < 0 || upperBound < 0) {
            throw new IllegalArgumentException("导出分页参数不合法");
        }
        List<SystemTaskLedgerExportRow> fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        List<SystemTaskLedgerExportRow> selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(SystemTaskLedgerExportRow row) {
        return Map.of("TASK_TYPE", row.taskType(),
                "SUBMITTER_ID", row.submitterId() == null ? "" : row.submitterId().toString(),
                "REVIEWER_ID", row.reviewerId() == null ? "" : row.reviewerId().toString(),
                "STATUS", row.status(),
                "CREATED_AT", row.createdAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                "REVIEW_COMMENT", row.reviewComment() == null ? "" : row.reviewComment());
    }
}
