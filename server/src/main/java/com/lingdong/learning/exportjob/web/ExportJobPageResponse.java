package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.ExportJobPage;

import java.util.List;

/** 导出作业分页响应。 */
public record ExportJobPageResponse(
        List<ExportJobResponse> items,
        int page,
        int pageSize,
        long total
) {
    static ExportJobPageResponse from(ExportJobPage page) {
        return new ExportJobPageResponse(
                page.items().stream().map(ExportJobResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
