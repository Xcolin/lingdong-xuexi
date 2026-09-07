package com.lingdong.learning.exceptionreport.domain;

/** 异常报备只允许从已提交单向流转为已处理。 */
public enum ExceptionReportStatus {
    SUBMITTED,
    HANDLED
}
