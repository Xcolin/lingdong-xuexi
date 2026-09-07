package com.lingdong.learning.attendance.application;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import java.time.LocalTime;

/** 一名学生的人工登记输入，首次登记版本为空。 */
public record AttendanceEntry(Long studentId, AttendanceStatus status,
        LocalTime checkinTime, LocalTime checkoutTime, Long versionNo) { }
