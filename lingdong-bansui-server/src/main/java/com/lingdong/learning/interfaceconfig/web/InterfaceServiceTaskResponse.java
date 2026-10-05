package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskStatus;

import java.time.LocalDateTime;
import java.util.function.Function;

/** 接口服务系统任务审核结果；审核人展示为姓名。 */
public record InterfaceServiceTaskResponse(
        String taskId,
        SystemTaskStatus status,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment
) {
    static InterfaceServiceTaskResponse from(SystemTask task, Function<Long, String> nameOf) {
        return new InterfaceServiceTaskResponse(
                task.id().toString(), task.status(),
                nameOf.apply(task.reviewedBy()),
                task.reviewedAt(), task.reviewComment()
        );
    }
}
