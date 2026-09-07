package com.lingdong.learning.exceptionreport.application;

import java.util.List;

/** 异常报备分页结果。 */
public record ExceptionReportPage(List<ExceptionReportView> items, int page, int pageSize, long total) {
}
