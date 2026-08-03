package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;

/** 任务原始目标响应。 */
public record LearningTaskTargetResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        LearningTaskTargetType targetType,
        @JsonSerialize(using = ToStringSerializer.class) Long targetId
) {
    static LearningTaskTargetResponse from(LearningTaskTarget target) {
        return new LearningTaskTargetResponse(target.id(), target.targetType(), target.targetId());
    }
}
