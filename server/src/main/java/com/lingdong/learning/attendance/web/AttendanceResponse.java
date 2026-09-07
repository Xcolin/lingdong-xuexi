package com.lingdong.learning.attendance.web;

import com.lingdong.learning.attendance.infrastructure.persistence.AttendanceRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 所有雪花标识以字符串传输，姓名只显示首字与掩码。 */
public record AttendanceResponse(String id, String studentId, String studentName,
        String classOrganizationId, String className, LocalDate attendanceDate, String status,
        LocalTime checkinTime, LocalTime checkoutTime, String source, String recordedBy,
        String recorderName, long versionNo, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AttendanceResponse from(AttendanceRow row) {
        return new AttendanceResponse(row.id().toString(), row.studentId().toString(), mask(row.studentName()),
                row.classOrganizationId().toString(), row.className(), row.attendanceDate(), row.status().name(),
                row.checkinTime(), row.checkoutTime(), row.source(), row.recordedBy().toString(),
                row.recorderName(), row.versionNo(), row.createdAt(), row.updatedAt());
    }

    static String mask(String name) {
        return name == null || name.isBlank() ? "*" : name.substring(0, name.offsetByCodePoints(0, 1)) + "*";
    }
}
