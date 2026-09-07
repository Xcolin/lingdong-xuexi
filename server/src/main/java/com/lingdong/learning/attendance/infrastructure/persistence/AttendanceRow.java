package com.lingdong.learning.attendance.infrastructure.persistence;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 事实台账联表结果，学生姓名在响应边界统一脱敏。 */
public record AttendanceRow(Long id, Long studentId, String studentName, Long classOrganizationId,
        String className, LocalDate attendanceDate, AttendanceStatus status,
        LocalTime checkinTime, LocalTime checkoutTime, String source, Long recordedBy,
        String recorderName, long versionNo, LocalDateTime createdAt, LocalDateTime updatedAt) { }
