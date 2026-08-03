package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;

/** 任务草稿中的一个原始目标。 */
public record LearningTaskTargetInput(LearningTaskTargetType targetType, Long targetId) {
}
