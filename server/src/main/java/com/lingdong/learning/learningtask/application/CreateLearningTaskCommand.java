package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

/** 创建任务草稿命令，操作者始终来自认证会话。 */
public record CreateLearningTaskCommand(
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        Long reviewerUserId,
        LearningTaskDraftInput draft
) {
}
