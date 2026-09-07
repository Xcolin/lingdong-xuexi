package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobType;

import java.time.LocalDateTime;

/** 审核员可见的敏感导出申请元数据，不含样例和结果文件。 */
public record ExportJobReviewView(
        Long jobId,
        Long systemTaskId,
        String requesterName,
        ExportJobType exportType,
        String scopeSummary,
        String requestReason,
        LocalDateTime requestedAt
) { }
