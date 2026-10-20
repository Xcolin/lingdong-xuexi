package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.TaskPauseType;

/** 学生暂停本人任务命令；调节方式与原因说明来自原型交互（05-06/05-07）。 */
public record PauseTaskCommand(TaskPauseType pauseType, int durationMinutes, String adjustMethod, String reasonNote) {
    public PauseTaskCommand(TaskPauseType pauseType, int durationMinutes) {
        this(pauseType, durationMinutes, null, null);
    }
}
