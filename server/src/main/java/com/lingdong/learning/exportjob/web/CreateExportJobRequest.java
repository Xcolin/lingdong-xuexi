package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.CreateExportJobCommand;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Web 创建导出请求，不接受 SQL、实体字段名或来源地址字段。 */
public record CreateExportJobRequest(
        @NotNull ExportJobType exportType,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = ExportStudentIdDeserializer.class)
        String studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        @Size(max = 10) List<@NotBlank @Size(max = 64) String> columns,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 64) String dictionaryTypeCode,
        @Size(max = 16) String dictionaryStatus,
        @Size(max = 16) String templateType,
        @Size(max = 64) String templateModuleCode,
        @Size(max = 16) String templateStatus,
        @Size(max = 100) String interfaceCallerName,
        @Size(max = 16) String interfaceStatus,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = InterfaceOwnerIdDeserializer.class)
        @Size(max = 19) String interfaceOwnerId,
        @Size(max = 32) String cacheDomain,
        @Size(max = 16) String cacheStatus,
        @Size(max = 64) String systemTaskType,
        @Size(max = 32) String systemTaskStatus,
        @Size(max = 32) String rewardExchangeStatus,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = InterfaceOwnerIdDeserializer.class)
        @Size(max = 19) String exceptionClassId,
        @Size(max = 32) String exceptionType,
        @Size(max = 32) String exceptionStatus,
        @Size(max = 64) String attachmentModuleCode,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = InterfaceOwnerIdDeserializer.class)
        @Size(max = 19) String attachmentUploaderId,
        @Size(max = 64) String attachmentFileCategory
) {
    public CreateExportJobRequest(ExportJobType exportType, String studentId, LocalDateTime startedAt, LocalDateTime endedAt,
            IamChangeAuditEventType eventType, List<String> columns, String reason, String dictionaryTypeCode, String dictionaryStatus,
            String templateType, String templateModuleCode, String templateStatus, String interfaceCallerName, String interfaceStatus,
            String interfaceOwnerId, String cacheDomain, String cacheStatus, String systemTaskType, String systemTaskStatus, String rewardExchangeStatus) {
        this(exportType, studentId, startedAt, endedAt, eventType, columns, reason, dictionaryTypeCode, dictionaryStatus,
                templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId,
                cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, null, null, null, null, null, null);
    }

    CreateExportJobCommand toCommand(Long requesterId, String requestSource) {
        if (exportType == ExportJobType.REWARD_EXCHANGE_LEDGER
                && (studentId == null || !studentId.matches("[1-9][0-9]{18}"))) {
            throw new IllegalArgumentException("奖励兑换导出必须指定19位字符串学生标识");
        }
        return new CreateExportJobCommand(
                requesterId, exportType, parseId(studentId), startedAt, endedAt,
                eventType, columns, reason, requestSource, dictionaryTypeCode, dictionaryStatus,
                templateType, templateModuleCode, templateStatus, interfaceCallerName, interfaceStatus, interfaceOwnerId, cacheDomain, cacheStatus, systemTaskType, systemTaskStatus, rewardExchangeStatus, exceptionClassId, exceptionType, exceptionStatus, attachmentModuleCode, attachmentUploaderId, attachmentFileCategory);
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("学生标识必须为正整数");
        }
    }
}
