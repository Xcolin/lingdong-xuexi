package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChange;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeType;

/** 已提交接口服务变更任务的最小响应。 */
public record InterfaceServiceSubmissionResponse(
        String changeId,
        String taskId,
        String serviceId,
        InterfaceServiceChangeType changeType
) {
    static InterfaceServiceSubmissionResponse from(InterfaceServiceChange change) {
        return new InterfaceServiceSubmissionResponse(
                change.id().toString(),
                change.taskId().toString(),
                change.serviceId() == null ? null : change.serviceId().toString(),
                change.changeType()
        );
    }
}
