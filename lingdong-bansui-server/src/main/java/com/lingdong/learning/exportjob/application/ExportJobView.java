package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;

import java.time.LocalDateTime;

/** 不暴露来源摘要、存储键和原始 JSON 的导出作业视图。 */
public record ExportJobView(
        Long id,
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
    public static ExportJobView from(ExportJobRecord job) {
        return new ExportJobView(
                job.id(), job.jobCode(), job.exportType(), job.templateName(),
                job.templateVersion(), job.status(), job.totalRows(), job.processedRows(),
                job.failureCode(), job.failureMessage(), job.requestReason(),
                job.requestedAt(), job.completedAt());
    }
}
