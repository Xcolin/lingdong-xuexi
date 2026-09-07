package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.ExportJobReviewPage;

import java.util.List;

/** 敏感导出待审分页响应。 */
public record ExportJobReviewPageResponse(
        List<ExportJobReviewResponse> items,
        int page,
        int pageSize,
        long total
) {
    static ExportJobReviewPageResponse from(ExportJobReviewPage page) {
        return new ExportJobReviewPageResponse(
                page.items().stream().map(ExportJobReviewResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
