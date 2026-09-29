package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;

import java.util.List;

/** 任务定义、原始目标和标签的只读聚合。 */
public record LearningTaskDetails(
        LearningTask task,
        List<LearningTaskTarget> targets,
        List<String> tagCodes
) {
    public LearningTaskDetails {
        targets = List.copyOf(targets);
        tagCodes = List.copyOf(tagCodes);
    }
}
