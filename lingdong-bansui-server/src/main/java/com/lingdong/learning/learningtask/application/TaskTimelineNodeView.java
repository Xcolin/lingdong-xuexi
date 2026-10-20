package com.lingdong.learning.learningtask.application;

import java.time.LocalDateTime;

/** 学生任务执行时间线节点（05-05），由审计事件推导。 */
public record TaskTimelineNodeView(
        String eventType,
        String reason,
        String eventDetails,
        LocalDateTime occurredAt
) {
}
