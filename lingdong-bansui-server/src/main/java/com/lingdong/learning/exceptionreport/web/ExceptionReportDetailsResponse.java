package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.ExceptionReportDetails;

import java.util.List;

/** 异常报备详情响应。 */
public record ExceptionReportDetailsResponse(
        ExceptionReportResponse report, List<ExceptionReportActionResponse> actions
) {
    static ExceptionReportDetailsResponse from(ExceptionReportDetails details) {
        return new ExceptionReportDetailsResponse(ExceptionReportResponse.from(details.report()),
                details.actions().stream().map(ExceptionReportActionResponse::from).toList());
    }
}
