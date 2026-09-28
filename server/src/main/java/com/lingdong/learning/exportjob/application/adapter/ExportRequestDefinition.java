package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;

import java.time.LocalDateTime;

/** 固化后的受控导出筛选，不包含客户端字段名或查询表达式。 */
public record ExportRequestDefinition(
        Long requesterId,
        Long studentId,
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
        Long interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus, Boolean systemTaskAuditor, java.util.List<com.lingdong.learning.audit.application.SystemTaskType> systemTaskTypes,
        String rewardExchangeStatus,
        String exceptionType, String exceptionStatus, Boolean exceptionTeacherOnly, java.util.List<Long> exceptionClassIds,
        String attachmentModuleCode, String attachmentUploaderId, String attachmentFileCategory
) {
    public ExportRequestDefinition(Long requesterId,
        Long studentId,
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
        Long interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus, Boolean systemTaskAuditor, java.util.List<com.lingdong.learning.audit.application.SystemTaskType> systemTaskTypes,
        String rewardExchangeStatus,
        String exceptionType, String exceptionStatus, Boolean exceptionTeacherOnly, java.util.List<Long> exceptionClassIds) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, systemTaskAuditor, systemTaskTypes, rewardExchangeStatus, exceptionType, exceptionStatus, exceptionTeacherOnly, exceptionClassIds, null, null, null);
    }

    public ExportRequestDefinition(Long requesterId,
        Long studentId,
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
        Long interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus, Boolean systemTaskAuditor, java.util.List<com.lingdong.learning.audit.application.SystemTaskType> systemTaskTypes,
        String rewardExchangeStatus) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, systemTaskAuditor, systemTaskTypes, rewardExchangeStatus, null, null, null, null);
    }

    public ExportRequestDefinition(Long requesterId,
        Long studentId,
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
        Long interfaceOwnerId,
        String cacheDomain,
        String cacheStatus,
        String systemTaskType, String systemTaskStatus, Boolean systemTaskAuditor, java.util.List<com.lingdong.learning.audit.application.SystemTaskType> systemTaskTypes) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, systemTaskAuditor, systemTaskTypes, null);
    }
    public ExportRequestDefinition(Long requesterId, Long studentId, LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus, Long interfaceOwnerId, String cacheDomain, String cacheStatus) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, null, null, null, null);
    }

    public ExportRequestDefinition(Long requesterId, Long studentId, LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus, Long interfaceOwnerId) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, null, null);
    }

    public ExportRequestDefinition(Long requesterId, Long studentId, LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, null, null, null);
    }

    public ExportRequestDefinition(Long requesterId, Long studentId, LocalDateTime startedAt,
            LocalDateTime endedAt, IamChangeAuditEventType eventType, String dictionaryTypeCode, String dictionaryStatus) {
        this(requesterId, studentId, startedAt, endedAt, eventType, dictionaryTypeCode, dictionaryStatus, null, null, null);
    }
    public ExportRequestDefinition(Long requesterId, Long studentId, LocalDateTime startedAt,
            LocalDateTime endedAt, IamChangeAuditEventType eventType) {
        this(requesterId, studentId, startedAt, endedAt, eventType, null, null);
    }
}
