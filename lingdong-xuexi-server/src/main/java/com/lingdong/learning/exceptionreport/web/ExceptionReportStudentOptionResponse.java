package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.ExceptionReportStudentOption;

/** 异常报备学生选项响应。 */
public record ExceptionReportStudentOptionResponse(String studentId, String studentName, String studentAccountMasked) {
    static ExceptionReportStudentOptionResponse from(ExceptionReportStudentOption option) {
        return new ExceptionReportStudentOptionResponse(String.valueOf(option.studentId()),
                option.studentName(), option.studentAccountMasked());
    }
}
