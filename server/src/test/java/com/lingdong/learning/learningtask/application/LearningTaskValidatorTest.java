package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningTaskValidatorTest {
    private DictionaryQueryService dictionaryQueryService;
    private LearningTaskValidator validator;

    @BeforeEach
    void setUp() {
        dictionaryQueryService = mock(DictionaryQueryService.class);
        when(dictionaryQueryService.findEnabledItems("TASK_CATEGORY")).thenReturn(List.of(
                DictionaryItem.enabled(1L, 11L, "GENERAL", "通用任务", 10, true)));
        when(dictionaryQueryService.findEnabledItems("TASK_TAG")).thenReturn(List.of(
                DictionaryItem.enabled(2L, 12L, "DAILY", "日常", 10, false)));
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-01T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        validator = new LearningTaskValidator(dictionaryQueryService, clock);
    }

    @Test
    void normalizesAndValidatesADraftDefinition() {
        ValidatedLearningTaskDraft result = validator.validate(new LearningTaskDraftInput(
                "  每日阅读  ", 2, 30, LocalDate.of(2026, 8, 1), "general",
                List.of("daily", " DAILY "), "  阅读三十分钟  ",
                List.of(
                        new LearningTaskTargetInput(LearningTaskTargetType.STUDENT, 1001L),
                        new LearningTaskTargetInput(LearningTaskTargetType.STUDENT, 1001L))));

        assertThat(result.title()).isEqualTo("每日阅读");
        assertThat(result.basePoints()).isEqualTo(20);
        assertThat(result.categoryCode()).isEqualTo("GENERAL");
        assertThat(result.tagCodes()).containsExactly("DAILY");
        assertThat(result.remark()).isEqualTo("阅读三十分钟");
        assertThat(result.targets()).containsExactly(
                new LearningTaskTargetInput(LearningTaskTargetType.STUDENT, 1001L));
    }

    @Test
    void rejectsInvalidFieldsDatesDictionariesAndTargets() {
        assertThatThrownBy(() -> validator.validate(validInput(" ", 1, 30,
                LocalDate.of(2026, 8, 1), null, List.of(), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 4, 30,
                LocalDate.of(2026, 8, 1), null, List.of(), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 1, 0,
                LocalDate.of(2026, 8, 1), null, List.of(), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 1, 30,
                LocalDate.of(2026, 7, 31), null, List.of(), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 1, 30,
                LocalDate.of(2026, 8, 1), "UNKNOWN", List.of(), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 1, 30,
                LocalDate.of(2026, 8, 1), null, List.of("UNKNOWN"), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 1, 30,
                LocalDate.of(2026, 8, 1), null, List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMoreThanTwentyTagsAndOverlongText() {
        List<String> tags = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> "TAG_" + index)
                .toList();
        assertThatThrownBy(() -> validator.validate(validInput("阅读", 1, 30,
                LocalDate.of(2026, 8, 1), null, tags, List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(validInput("读".repeat(51), 1, 30,
                LocalDate.of(2026, 8, 1), null, List.of(), List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new LearningTaskDraftInput(
                "阅读", 1, 30, LocalDate.of(2026, 8, 1), null, List.of(), "备".repeat(201),
                List.of(target()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LearningTaskDraftInput validInput(
            String title,
            Integer difficulty,
            Integer duration,
            LocalDate date,
            String category,
            List<String> tags,
            List<LearningTaskTargetInput> targets
    ) {
        return new LearningTaskDraftInput(
                title, difficulty, duration, date, category, tags, null, targets);
    }

    private LearningTaskTargetInput target() {
        return new LearningTaskTargetInput(LearningTaskTargetType.STUDENT, 1001L);
    }
}
