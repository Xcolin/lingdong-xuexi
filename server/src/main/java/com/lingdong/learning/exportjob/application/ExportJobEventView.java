package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobEventRecord;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;

import java.time.LocalDateTime;

/** 可展示的导出作业关键事件。 */
public record ExportJobEventView(
        String id,
        ExportJobEventType eventType,
        String summary,
        LocalDateTime occurredAt
) {
    static ExportJobEventView from(ExportJobEventRecord event) {
        return new ExportJobEventView(
                event.id().toString(), event.eventType(), event.summary(), event.occurredAt());
    }
}
