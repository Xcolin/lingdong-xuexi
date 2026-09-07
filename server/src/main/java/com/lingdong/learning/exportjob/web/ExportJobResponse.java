package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.ExportJobView;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;

import java.time.LocalDateTime;

/** 导出作业响应中的雪花标识统一序列化为字符串。 */
public record ExportJobResponse(
        String id,
        String jobCode,
        ExportJobType exportType,
        String templateName,
        String templateVersion,
        ExportJobStatus status,
        long totalRows,
        long processedRows,
        String failureCode,
        String failureMessage,
        String requestReason,
        LocalDateTime requestedAt,
        LocalDateTime completedAt
) {
    static ExportJobResponse from(ExportJobRecord job) {
        return from(ExportJobView.from(job));
    }

    static ExportJobResponse from(ExportJobView job) {
        return new ExportJobResponse(
                job.id().toString(), job.jobCode(), job.exportType(), job.templateName(),
                job.templateVersion(), job.status(), job.totalRows(), job.processedRows(),
                job.failureCode(), job.failureMessage(), job.requestReason(),
                job.requestedAt(), job.completedAt());
    }
}
