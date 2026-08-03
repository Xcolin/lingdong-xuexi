package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.ActiveTaskPauseView;
import com.lingdong.learning.learningtask.domain.TaskPauseType;

import java.time.LocalDateTime;

/** 当前有效暂停响应。 */
public record ActiveTaskPauseResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        TaskPauseType pauseType,
        LocalDateTime startedAt,
        LocalDateTime expiresAt
) {
    static ActiveTaskPauseResponse from(ActiveTaskPauseView view) {
        return view == null ? null : new ActiveTaskPauseResponse(
                view.id(), view.pauseType(), view.startedAt(), view.expiresAt());
    }
}
