package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;

/** Request to change the authorization boundary of a registered interface service. */
public record CreateInterfaceServiceAuthorizationChangeCommand(
        Long submitterId,
        Long serviceId,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        String taskTitle,
        String taskDescription
) { }
