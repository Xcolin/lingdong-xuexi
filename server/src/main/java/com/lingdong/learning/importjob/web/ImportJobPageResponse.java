package com.lingdong.learning.importjob.web;

import com.lingdong.learning.importjob.application.ImportJobPage;

import java.util.List;

/** 导入校验作业分页响应。 */
public record ImportJobPageResponse(
        List<ImportJobResponse> items, int page, int pageSize, long total
) {
    public static ImportJobPageResponse from(ImportJobPage page) {
        return new ImportJobPageResponse(
                page.items().stream().map(ImportJobResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
