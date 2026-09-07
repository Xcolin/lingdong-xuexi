package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.HandleExceptionReportCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 机构管理员处理异常报备请求。 */
public record HandleExceptionReportRequest(
        @PositiveOrZero long versionNo,
        @NotBlank @Size(max = 1000) String handlingNote
) {
    HandleExceptionReportCommand toCommand() { return new HandleExceptionReportCommand(versionNo, handlingNote); }
}
