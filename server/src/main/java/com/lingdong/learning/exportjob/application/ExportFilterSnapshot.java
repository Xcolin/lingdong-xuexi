package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;

import java.time.LocalDateTime;

/** 作业创建时固化的规范化筛选条件。 */
public record ExportFilterSnapshot(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        String dictionaryTypeCode,
        String dictionaryStatus,
        String templateType,
        String templateModuleCode,
        String templateStatus,
        String interfaceCallerName,
        String interfaceStatus,
        String interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus,
        String rewardExchangeStatus,
        String exceptionClassId, String exceptionType, String exceptionStatus,
        String attachmentModuleCode, String attachmentUploaderId, String attachmentFileCategory
) {
    public ExportFilterSnapshot(LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        String dictionaryTypeCode,
        String dictionaryStatus,
        String templateType,
        String templateModuleCode,
        String templateStatus,
        String interfaceCallerName,
        String interfaceStatus,
        String interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus,
        String rewardExchangeStatus,
        String exceptionClassId, String exceptionType, String exceptionStatus) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, exceptionClassId, exceptionType, exceptionStatus, null, null, null);
    }

    public ExportFilterSnapshot(LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        String dictionaryTypeCode,
        String dictionaryStatus,
        String templateType,
        String templateModuleCode,
        String templateStatus,
        String interfaceCallerName,
        String interfaceStatus,
        String interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus,
        String rewardExchangeStatus) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, null, null, null);
    }

    public ExportFilterSnapshot(LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        String dictionaryTypeCode,
        String dictionaryStatus,
        String templateType,
        String templateModuleCode,
        String templateStatus,
        String interfaceCallerName,
        String interfaceStatus,
        String interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, null);
    }
    public ExportFilterSnapshot(LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus, String interfaceOwnerId, String cacheDomain, String cacheStatus) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, null, null);
    }

    public ExportFilterSnapshot(LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus, String interfaceOwnerId) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, null, null);
    }

    public ExportFilterSnapshot(LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, null, null, null);
    }

    public ExportFilterSnapshot(LocalDateTime startedAt, LocalDateTime endedAt,
            IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus) {
        this(startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, null, null, null);
    }
    public ExportFilterSnapshot(LocalDateTime startedAt, LocalDateTime endedAt,
            IamChangeAuditEventType eventType) {
        this(startedAt, endedAt, eventType, null, null);
    }
}
