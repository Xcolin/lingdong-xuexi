package com.lingdong.learning.attendance.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 班级、学生和日期唯一的考勤事实，历次更正另表保存。 */
public record AttendanceRecord(Long id, Long studentId, Long classOrganizationId,
        LocalDate attendanceDate, AttendanceStatus status, LocalTime checkinTime,
        LocalTime checkoutTime, String source, Long recordedBy, long versionNo,
        LocalDateTime createdAt, LocalDateTime updatedAt) { }
