package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.ExportJobReviewView;
import com.lingdong.learning.exportjob.domain.ExportJobType;

import java.time.LocalDateTime;

/** 系统审核员可见的敏感导出申请元数据。 */
public record ExportJobReviewResponse(
        String jobId,
        String systemTaskId,
        String requesterName,
        ExportJobType exportType,
        String scopeSummary,
        String requestReason,
        LocalDateTime requestedAt
) {
    static ExportJobReviewResponse from(ExportJobReviewView view) {
        return new ExportJobReviewResponse(
                view.jobId().toString(), view.systemTaskId().toString(),
                view.requesterName(), view.exportType(), view.scopeSummary(),
                view.requestReason(), view.requestedAt());
    }
}
