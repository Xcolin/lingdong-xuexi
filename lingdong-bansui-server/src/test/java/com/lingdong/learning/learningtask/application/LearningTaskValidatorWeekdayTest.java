package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LearningTaskValidatorWeekdayTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 14);

    private LearningTaskValidator validator() {
        DictionaryQueryService dictionary = mock(DictionaryQueryService.class);
        when(dictionary.findEnabledItems("TASK_TAG")).thenReturn(List.of());
        return new LearningTaskValidator(dictionary, Clock.fixed(
                Instant.parse("2026-10-14T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private LearningTaskDraftInput input(String frequency, List<Integer> weekdays) {
        return new LearningTaskDraftInput("阅读", 1, 30, START, null, List.of(), null,
                List.of(new LearningTaskTargetInput(LearningTaskTargetType.STUDENT, 1L)),
                true, null, frequency, weekdays);
    }

    @Test void weeklyOmittedWeekdaysDefaultToStartWednesday() {
        var result = validator().validate(input("WEEKLY", null));
        assertEquals("WEEKLY", result.recurrenceFrequency());
        assertEquals(List.of(3), result.recurrenceWeekdays());
    }

    @Test void dailyRejectsInvalidWeekdays() {
        assertThrows(IllegalArgumentException.class,
                () -> validator().validate(input("DAILY", List.of(0, 8))));
    }

    @Test void omittedNewPayloadFieldsPreserveDaily() {
        var result = validator().validate(input(null, null));
        assertEquals("DAILY", result.recurrenceFrequency());
        assertEquals(List.of(), result.recurrenceWeekdays());
    }

    @Test void customRejectsStartOutsideSelectedWeekdays() {
        assertThrows(IllegalArgumentException.class,
                () -> validator().validate(input("CUSTOM", List.of(1, 5))));
    }
}
