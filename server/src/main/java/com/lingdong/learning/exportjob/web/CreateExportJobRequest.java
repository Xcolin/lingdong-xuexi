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
        String studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        @Size(max = 10) List<@NotBlank @Size(max = 64) String> columns,
        @NotBlank @Size(max = 500) String reason
) {
    CreateExportJobCommand toCommand(Long requesterId, String requestSource) {
        return new CreateExportJobCommand(
                requesterId, exportType, parseId(studentId), startedAt, endedAt,
                eventType, columns, reason, requestSource);
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
