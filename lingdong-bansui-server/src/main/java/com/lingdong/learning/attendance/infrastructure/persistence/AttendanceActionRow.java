package com.lingdong.learning.attendance.infrastructure.persistence;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 只追加的操作历史查询结果。 */
public record AttendanceActionRow(Long id, String actionType, Long operatorUserId, String operatorName,
        AttendanceStatus beforeStatus, AttendanceStatus afterStatus,
        LocalTime beforeCheckinTime, LocalTime afterCheckinTime,
        LocalTime beforeCheckoutTime, LocalTime afterCheckoutTime, LocalDateTime createdAt) { }
