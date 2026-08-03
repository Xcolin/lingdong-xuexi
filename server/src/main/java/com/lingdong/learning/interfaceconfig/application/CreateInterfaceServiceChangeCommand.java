package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;

/** Request to register a new interface service through the system-audit workflow. */
public record CreateInterfaceServiceChangeCommand(
        Long submitterId,
        String serviceName,
        InterfaceDirection direction,
        InterfacePurpose purpose,
        String callerName,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        Long ownerId,
        String taskTitle,
        String taskDescription
) { }
