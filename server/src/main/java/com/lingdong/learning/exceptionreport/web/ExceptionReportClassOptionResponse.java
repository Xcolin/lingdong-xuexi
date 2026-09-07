package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption;

/** 异常报备班级选项响应。 */
public record ExceptionReportClassOptionResponse(String classOrganizationId, String className) {
    static ExceptionReportClassOptionResponse from(ExceptionReportClassOption option) {
        return new ExceptionReportClassOptionResponse(String.valueOf(option.classOrganizationId()), option.className());
    }
}
