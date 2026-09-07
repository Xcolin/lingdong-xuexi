package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.ExceptionReportPage;

import java.util.List;

/** 异常报备分页响应。 */
public record ExceptionReportPageResponse(
        List<ExceptionReportResponse> items, int page, int pageSize, long total
) {
    static ExceptionReportPageResponse from(ExceptionReportPage page) {
        return new ExceptionReportPageResponse(page.items().stream().map(ExceptionReportResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
