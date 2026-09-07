package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskStatus;

import java.time.LocalDateTime;

/** 接口服务系统任务审核结果。 */
public record InterfaceServiceTaskResponse(
        String taskId,
        SystemTaskStatus status,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment
) {
    static InterfaceServiceTaskResponse from(SystemTask task) {
        return new InterfaceServiceTaskResponse(
                task.id().toString(), task.status(),
                task.reviewedBy() == null ? null : task.reviewedBy().toString(),
                task.reviewedAt(), task.reviewComment()
        );
    }
}
