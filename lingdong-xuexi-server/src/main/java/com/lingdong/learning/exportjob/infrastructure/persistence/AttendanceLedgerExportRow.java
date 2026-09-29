package com.lingdong.learning.exportjob.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalTime;

/** 考勤台账导出行：一条 attendance_record 事实（班级×学生×上海自然日）。 */
public record AttendanceLedgerExportRow(Long id, String studentName, String className,
        LocalDate attendanceDate, String status, LocalTime checkinTime, LocalTime checkoutTime) {
}
