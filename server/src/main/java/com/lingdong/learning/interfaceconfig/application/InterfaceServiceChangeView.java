package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeType;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;

import java.time.LocalDateTime;

/** 接口服务变更快照及其系统任务状态的只读视图。 */
public record InterfaceServiceChangeView(
        Long changeId,
        Long taskId,
        Long serviceId,
        InterfaceServiceChangeType changeType,
        String serviceName,
        InterfaceDirection direction,
        InterfacePurpose purpose,
        String callerName,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        Long ownerId,
        InterfaceServiceStatus targetStatus,
        String taskTitle,
        String taskDescription,
        SystemTaskStatus taskStatus,
        Long submittedBy,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment,
        LocalDateTime createdAt
) { }
