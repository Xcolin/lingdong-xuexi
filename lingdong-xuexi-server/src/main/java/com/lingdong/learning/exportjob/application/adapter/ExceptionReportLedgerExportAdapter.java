package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExceptionReportExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExceptionReportExportRow;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class ExceptionReportLedgerExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("STUDENT_NAME", "学生姓名", true),
            new ExportColumnDefinition("EXCEPTION_TYPE", "异常类型", true),
            new ExportColumnDefinition("REPORTER_NAME", "报备教师", true),
            new ExportColumnDefinition("STATUS", "处理状态", true),
            new ExportColumnDefinition("REPORTED_AT", "报备时间", true));
    private final ExceptionReportExportMapper mapper;
    public ExceptionReportLedgerExportAdapter(ExceptionReportExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.EXCEPTION_REPORT_LEDGER; }
    @Override public boolean sensitive() { return false; }
    @Override public List<ExportColumnDefinition> columns() { return COLUMNS; }
    @Override public long captureUpperBound(ExportRequestDefinition request) {
        requireScope(request); Long upper = mapper.findUpperBound(request); return upper == null ? 0 : upper;
    }
    @Override public long count(ExportRequestDefinition request, long upperBound) {
        requireScope(request); return mapper.count(request, upperBound);
    }
    @Override public ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit) {
        requireScope(request);
        if (limit < 1 || limit == Integer.MAX_VALUE || cursor < 0 || upperBound < 0) throw new IllegalArgumentException("导出分页参数不合法");
        var fetched = mapper.findAfter(request, upperBound, cursor, limit + 1);
        boolean more = fetched.size() > limit;
        var selected = more ? fetched.subList(0, limit) : fetched;
        return new ExportDataPage(selected.stream().map(this::values).toList(),
                selected.isEmpty() ? null : selected.get(selected.size() - 1).id(), more);
    }
    private Map<String, Object> values(ExceptionReportExportRow row) {
        return Map.of("STUDENT_NAME", ExportMasking.familyName(row.studentName()),
                "EXCEPTION_TYPE", row.exceptionType(), "REPORTER_NAME", ExportMasking.familyName(row.reporterName()),
                "STATUS", row.status(), "REPORTED_AT", TIME.format(row.reportedAt()));
    }
    private void requireScope(ExportRequestDefinition request) {
        if (request == null || request.requesterId() == null || request.studentId() != null
                || request.exceptionTeacherOnly() == null || request.exceptionClassIds() == null)
            throw new IllegalArgumentException("异常报备导出必须固化身份和班级范围");
    }
}
