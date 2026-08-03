package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.LearningTaskTargetInput;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 任务原始目标请求。 */
public record LearningTaskTargetRequest(
        @NotNull LearningTaskTargetType targetType,
        @NotNull @Positive Long targetId
) {
    LearningTaskTargetInput toInput() {
        return new LearningTaskTargetInput(targetType, targetId);
    }
}
