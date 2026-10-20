package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.TaskTimelineNodeView;

import java.time.LocalDateTime;
import java.util.List;

/** 学生任务执行时间线节点响应。 */
public record TaskTimelineNodeResponse(
        String eventType,
        String reason,
        String eventDetails,
        LocalDateTime occurredAt
) {
    static TaskTimelineNodeResponse from(TaskTimelineNodeView view) {
        return new TaskTimelineNodeResponse(
                view.eventType(), view.reason(), view.eventDetails(), view.occurredAt());
    }

    static List<TaskTimelineNodeResponse> from(List<TaskTimelineNodeView> views) {
        return views.stream().map(TaskTimelineNodeResponse::from).toList();
    }
}
