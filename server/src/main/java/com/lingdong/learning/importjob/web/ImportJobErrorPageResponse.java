package com.lingdong.learning.importjob.web;

import com.lingdong.learning.importjob.application.ImportJobRowResultPage;

import java.util.List;

/** 导入校验错误行分页响应。 */
public record ImportJobErrorPageResponse(
        List<ImportJobErrorResponse> items, int page, int pageSize, long total
) {
    public static ImportJobErrorPageResponse from(ImportJobRowResultPage page) {
        return new ImportJobErrorPageResponse(
                page.items().stream().map(ImportJobErrorResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
