package com.lingdong.learning.attendance.application;

import com.lingdong.learning.attendance.domain.AttendanceStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 人工考勤规则不能把未登记推算为缺勤，非法批次必须在写入前拒绝。 */
class AttendanceRulesTest {
    private final LocalDate today = LocalDate.of(2026, 9, 7);

    @Test void acceptsLeaveWithoutTimesAndOptionalArrivalTime() {
        assertThatCode(() -> AttendanceRules.validate(today, today, List.of(
                entry(1L, AttendanceStatus.LEAVE, null, null),
                entry(2L, AttendanceStatus.LATE, LocalTime.of(9, 0), null))))
                .doesNotThrowAnyException();
    }

    @Test void rejectsFutureDateDuplicateStudentsAndEmptyBatch() {
        var value = entry(1L, AttendanceStatus.NORMAL, null, null);
        assertThatThrownBy(() -> AttendanceRules.validate(today.plusDays(1), today, List.of(value)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRules.validate(today, today, List.of(value, value)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AttendanceRules.validate(today, today, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsContradictoryTimesAndSubMinutePrecision() {
        for (var value : List.of(
                entry(1L, AttendanceStatus.ABSENT, LocalTime.of(8, 0), null),
                entry(1L, AttendanceStatus.LEAVE, null, LocalTime.of(16, 0)),
                entry(1L, AttendanceStatus.NORMAL, LocalTime.of(16, 0), LocalTime.of(8, 0)),
                entry(1L, AttendanceStatus.NORMAL, LocalTime.of(8, 0, 1), null))) {
            assertThatThrownBy(() -> AttendanceRules.validate(today, today, List.of(value)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private AttendanceEntry entry(Long id, AttendanceStatus status, LocalTime in, LocalTime out) {
        return new AttendanceEntry(id, status, in, out, null);
    }
}
