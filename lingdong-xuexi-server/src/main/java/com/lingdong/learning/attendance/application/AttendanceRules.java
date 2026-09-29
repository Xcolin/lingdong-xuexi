package com.lingdong.learning.attendance.application;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;

/** 无外部依赖的批量规则，在事务写入前整体校验。 */
public final class AttendanceRules {
    private AttendanceRules() { }

    public static void validate(LocalDate date, LocalDate today, List<AttendanceEntry> items) {
        if (date == null || date.isAfter(today) || items == null || items.isEmpty() || items.size() > 100) {
            throw new IllegalArgumentException("考勤日期或人数不合法");
        }
        var students = new HashSet<Long>();
        for (AttendanceEntry entry : items) {
            if (entry == null || entry.studentId() == null || entry.studentId() <= 0 || entry.status() == null
                    || !students.add(entry.studentId()) || entry.versionNo() != null && entry.versionNo() < 0) {
                throw new IllegalArgumentException("考勤学生、状态或版本不合法");
            }
            LocalTime in = entry.checkinTime();
            LocalTime out = entry.checkoutTime();
            if (!minutePrecision(in) || !minutePrecision(out) || in != null && out != null && in.isAfter(out)
                    || (entry.status() == AttendanceStatus.ABSENT || entry.status() == AttendanceStatus.LEAVE)
                    && (in != null || out != null)) {
                throw new IllegalArgumentException("考勤状态与签到签退时间不一致");
            }
        }
    }

    private static boolean minutePrecision(LocalTime time) {
        return time == null || time.getSecond() == 0 && time.getNano() == 0;
    }
}
