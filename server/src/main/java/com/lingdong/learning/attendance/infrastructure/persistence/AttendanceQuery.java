package com.lingdong.learning.attendance.infrastructure.persistence;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import java.time.LocalDate;

/** 范围、业务条件与分页一起传入 SQL，避免先全量查询再过滤。 */
public record AttendanceQuery(AttendanceScope scope, Long classOrganizationId, Long studentId,
        String keyword, AttendanceStatus status, LocalDate dateFrom, LocalDate dateTo, int limit, int offset) { }
