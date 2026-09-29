package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.ExportColumnSnapshot;
import com.lingdong.learning.exportjob.application.ExportJobDetailView;
import com.lingdong.learning.exportjob.application.ExportJobEventView;

import java.util.List;

/** 不返回原始快照 JSON、来源摘要或附件存储信息的详情响应。 */
public record ExportJobDetailResponse(
        ExportJobResponse job,
        List<ExportColumnSnapshot> columns,
        String scopeSummary,
        List<ExportJobEventView> events
) {
    static ExportJobDetailResponse from(ExportJobDetailView detail) {
        return new ExportJobDetailResponse(
                ExportJobResponse.from(detail.job()), detail.columns(),
                detail.scopeSummary(), detail.events());
    }
}
