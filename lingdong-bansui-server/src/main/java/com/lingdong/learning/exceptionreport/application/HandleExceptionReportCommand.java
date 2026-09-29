package com.lingdong.learning.exceptionreport.application;

/** 机构管理员处理异常报备命令。 */
public record HandleExceptionReportCommand(long versionNo, String handlingNote) {
}
