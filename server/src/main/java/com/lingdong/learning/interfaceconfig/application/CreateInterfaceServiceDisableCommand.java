package com.lingdong.learning.interfaceconfig.application;

/** Request to stop an already registered interface service through the system-audit workflow. */
public record CreateInterfaceServiceDisableCommand(
        Long submitterId,
        Long serviceId,
        String taskTitle,
        String taskDescription
) { }
