package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;

import java.time.LocalDateTime;
import java.util.List;

/** 创建导出作业的受控应用命令。 */
public record CreateExportJobCommand(
        Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource,
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
        String attachmentModuleCode, String attachmentUploaderId, String attachmentFileCategory,
        String studentTaskSource, String studentTaskStatus, String outputFormat, String orgStatClassId, String attClassId
) {
    public CreateExportJobCommand(Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource,
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
        String attachmentModuleCode, String attachmentUploaderId, String attachmentFileCategory) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, exceptionClassId, exceptionType, exceptionStatus, attachmentModuleCode, attachmentUploaderId, attachmentFileCategory, null, null, null, null, null);
    }

    public CreateExportJobCommand(Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource,
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
        String attachmentModuleCode, String attachmentUploaderId, String attachmentFileCategory,
        String studentTaskSource, String studentTaskStatus, String outputFormat) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, exceptionClassId, exceptionType, exceptionStatus, attachmentModuleCode, attachmentUploaderId, attachmentFileCategory, studentTaskSource, studentTaskStatus, outputFormat, null, null);
    }

    public CreateExportJobCommand(Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource,
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
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, exceptionClassId, exceptionType, exceptionStatus, null, null, null);
    }

    public CreateExportJobCommand(Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource,
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
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, null, null, null);
    }

    public CreateExportJobCommand(Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource,
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
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, null);
    }
    public CreateExportJobCommand(Long requesterId, ExportJobType exportType, Long studentId, LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, List<String> selectedColumns, String reason, String requestSource, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus, String interfaceOwnerId, String cacheDomain, String cacheStatus) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, null, null);
    }

    public CreateExportJobCommand(Long requesterId, ExportJobType exportType, Long studentId, LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, List<String> selectedColumns, String reason, String requestSource, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus, String interfaceOwnerId) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, null, null);
    }

    public CreateExportJobCommand(Long requesterId, ExportJobType exportType, Long studentId, LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType, List<String> selectedColumns, String reason, String requestSource, String dictionaryTypeCode, String dictionaryStatus, String templateType, String templateModuleCode, String templateStatus) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType, selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, templateType, templateModuleCode, templateStatus, null, null, null);
    }

    public CreateExportJobCommand(Long requesterId, ExportJobType exportType, Long studentId,
            LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType,
            List<String> selectedColumns, String reason, String requestSource,
            String dictionaryTypeCode, String dictionaryStatus) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType,
                selectedColumns, reason, requestSource, dictionaryTypeCode, dictionaryStatus, null, null, null);
    }
    public CreateExportJobCommand(Long requesterId, ExportJobType exportType, Long studentId,
            LocalDateTime startedAt, LocalDateTime endedAt, IamChangeAuditEventType eventType,
            List<String> selectedColumns, String reason, String requestSource) {
        this(requesterId, exportType, studentId, startedAt, endedAt, eventType,
                selectedColumns, reason, requestSource, null, null);
    }
}
