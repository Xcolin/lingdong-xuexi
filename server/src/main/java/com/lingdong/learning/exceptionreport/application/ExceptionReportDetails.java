package com.lingdong.learning.exceptionreport.application;

import java.util.List;

/** 异常报备详情及永久动作历史。 */
public record ExceptionReportDetails(ExceptionReportView report, List<ExceptionReportActionView> actions) {
}
