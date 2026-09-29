package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.AttendanceLedgerExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.AttendanceLedgerExportRow;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** 考勤台账事实行导出；学生与班级范围互斥，空集合失败关闭。 */
@Component
public class AttendanceLedgerExportAdapter implements ExportDatasetAdapter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<ExportColumnDefinition> COLUMNS = List.of(
            new ExportColumnDefinition("STUDENT_NAME", "学生姓名", true),
            new ExportColumnDefinition("CLASS_NAME", "班级", true),
            new ExportColumnDefinition("ATTENDANCE_DATE", "考勤日期", true),
            new ExportColumnDefinition("STATUS", "考勤状态", true),
            new ExportColumnDefinition("CHECKIN_TIME", "签到时间", true),
            new ExportColumnDefinition("CHECKOUT_TIME", "签退时间", true));
    private final AttendanceLedgerExportMapper mapper;
    public AttendanceLedgerExportAdapter(AttendanceLedgerExportMapper mapper) { this.mapper = mapper; }
    @Override public ExportJobType type() { return ExportJobType.ATTENDANCE_LEDGER; }
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
    private Map<String, Object> values(AttendanceLedgerExportRow row) {
        return Map.of("STUDENT_NAME", ExportMasking.familyName(row.studentName()),
                "CLASS_NAME", row.className(),
                "ATTENDANCE_DATE", DATE.format(row.attendanceDate()),
                "STATUS", row.status(),
                "CHECKIN_TIME", row.checkinTime() == null ? "" : TIME.format(row.checkinTime()),
                "CHECKOUT_TIME", row.checkoutTime() == null ? "" : TIME.format(row.checkoutTime()));
    }
    private void requireScope(ExportRequestDefinition request) {
        if (request == null || request.requesterId() == null) {
            throw new IllegalArgumentException("考勤台账导出必须固化身份与范围");
        }
        boolean family = request.studentId() != null;
        boolean staff = request.attClassIds() != null && !request.attClassIds().isEmpty();
        if (family == staff) throw new IllegalArgumentException("考勤台账导出学生与班级范围互斥");
    }
}
