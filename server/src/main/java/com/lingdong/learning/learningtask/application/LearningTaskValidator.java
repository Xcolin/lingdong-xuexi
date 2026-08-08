package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 集中校验任务字段、日期和共享字典，避免客户端推导业务值。 */
@Component
public class LearningTaskValidator {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TAG_COUNT = 20;

    private final DictionaryQueryService dictionaryQueryService;
    private final Clock clock;

    public LearningTaskValidator(DictionaryQueryService dictionaryQueryService, Clock clock) {
        this.dictionaryQueryService = dictionaryQueryService;
        this.clock = clock;
    }

    public ValidatedLearningTaskDraft validate(LearningTaskDraftInput input) {
        Objects.requireNonNull(input, "任务草稿不能为空");
        String title = requiredText(input.title(), "任务标题", 50);
        int difficulty = requireRange(input.difficultyLevel(), "任务难度", 1, 3);
        int duration = requireRange(input.durationMinutes(), "执行时长", 1, 1440);
        LocalDate scheduledDate = Objects.requireNonNull(input.scheduledDate(), "计划日期不能为空");
        if (scheduledDate.isBefore(LocalDate.now(clock.withZone(BUSINESS_ZONE)))) {
            throw new IllegalArgumentException("计划日期不能早于当天");
        }
        LocalDate recurrenceEndDate = input.recurrenceEndDate();
        if (!input.recurrenceEnabled() && recurrenceEndDate != null) {
            throw new IllegalArgumentException("非固定任务不能设置结束日期");
        }
        if (recurrenceEndDate != null && recurrenceEndDate.isBefore(scheduledDate)) {
            throw new IllegalArgumentException("固定任务结束日期不能早于计划日期");
        }

        String categoryCode = optionalCode(input.categoryCode());
        if (categoryCode != null) {
            requireEnabledDictionaryCode("TASK_CATEGORY", categoryCode, "任务分类");
        }
        List<String> tagCodes = normalizeTags(input.tagCodes());
        Set<String> enabledTagCodes = enabledCodes("TASK_TAG");
        if (!enabledTagCodes.containsAll(tagCodes)) {
            throw new IllegalArgumentException("任务标签不存在或已停用");
        }
        List<LearningTaskTargetInput> targets = normalizeTargets(input.targets());
        return new ValidatedLearningTaskDraft(
                title, difficulty, difficulty * 10, duration, scheduledDate, categoryCode,
                tagCodes, optionalText(input.remark(), "任务备注", 200), targets,
                input.recurrenceEnabled(), recurrenceEndDate);
    }

    private List<String> normalizeTags(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String code = optionalCode(value);
            if (code == null) {
                throw new IllegalArgumentException("任务标签编码不能为空");
            }
            normalized.add(code);
        }
        if (normalized.size() > MAX_TAG_COUNT) {
            throw new IllegalArgumentException("任务标签不能超过 20 个");
        }
        return List.copyOf(normalized);
    }

    private List<LearningTaskTargetInput> normalizeTargets(List<LearningTaskTargetInput> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("任务目标不能为空");
        }
        LinkedHashSet<LearningTaskTargetInput> normalized = new LinkedHashSet<>();
        for (LearningTaskTargetInput value : values) {
            if (value == null || value.targetType() == null || value.targetId() == null || value.targetId() <= 0) {
                throw new IllegalArgumentException("任务目标不合法");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private void requireEnabledDictionaryCode(String typeCode, String code, String fieldName) {
        if (!enabledCodes(typeCode).contains(code)) {
            throw new IllegalArgumentException(fieldName + "不存在或已停用");
        }
    }

    private Set<String> enabledCodes(String typeCode) {
        return dictionaryQueryService.findEnabledItems(typeCode).stream()
                .map(DictionaryItem::code)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private int requireRange(Integer value, String fieldName, int minimum, int maximum) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "必须在 " + minimum + " 至 " + maximum + " 之间");
        }
        return value;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, fieldName, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String optionalCode(String value) {
        String normalized = optionalText(value, "字典编码", 64);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
