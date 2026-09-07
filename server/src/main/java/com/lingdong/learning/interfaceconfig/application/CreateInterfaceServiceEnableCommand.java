package com.lingdong.learning.interfaceconfig.application;

/** 提交接口服务重新启用任务所需的不可变参数。 */
public record CreateInterfaceServiceEnableCommand(
        Long submitterId,
        Long serviceId,
        String taskTitle,
        String taskDescription
) { }
