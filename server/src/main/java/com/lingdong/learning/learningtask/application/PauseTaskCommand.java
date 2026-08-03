package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.TaskPauseType;

/** 学生暂停本人任务命令。 */
public record PauseTaskCommand(TaskPauseType pauseType, int durationMinutes) {
}
