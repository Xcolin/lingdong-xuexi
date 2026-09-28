package com.lingdong.learning.exportjob.infrastructure.persistence;

/** 接口服务台账仅投影现有公开管理字段，不关联用户个人信息。 */
public record InterfaceServiceLedgerExportRow(Long id, String serviceName, String purpose,
        String callerName, String authorizationScope, String authorizationScopeValue, String status, Long ownerId) { }
