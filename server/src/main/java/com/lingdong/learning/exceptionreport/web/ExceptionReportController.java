package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.exceptionreport.application.ExceptionReportApplicationService;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供教师和机构管理员共用的异常报备 REST 契约。 */
@RestController
@RequestMapping("/api/v1/exception-reports")
public class ExceptionReportController {
    private final ExceptionReportApplicationService service;
    public ExceptionReportController(ExceptionReportApplicationService service) { this.service = service; }

    @RequirePermission("EXCEPTION_REPORT_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExceptionReportResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateExceptionReportRequest request) {
        return ExceptionReportResponse.from(service.create(user, request.toCommand()));
    }

    @RequirePermission("EXCEPTION_REPORT_READ")
    @GetMapping
    public ExceptionReportPageResponse findPage(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long classOrganizationId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) ExceptionReportType exceptionType,
            @RequestParam(required = false) ExceptionReportStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ExceptionReportPageResponse.from(service.findPage(user, classOrganizationId,
                studentId, exceptionType, status, page, pageSize));
    }

    @RequirePermission("EXCEPTION_REPORT_READ")
    @GetMapping("/class-options")
    public List<ExceptionReportClassOptionResponse> classOptions(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return service.findClassOptions(user).stream().map(ExceptionReportClassOptionResponse::from).toList();
    }

    @RequirePermission("EXCEPTION_REPORT_CREATE")
    @GetMapping("/student-options")
    public List<ExceptionReportStudentOptionResponse> studentOptions(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long classOrganizationId) {
        return service.findStudentOptions(user, classOrganizationId).stream()
                .map(ExceptionReportStudentOptionResponse::from).toList();
    }

    @RequirePermission("EXCEPTION_REPORT_READ")
    @GetMapping("/{id}")
    public ExceptionReportDetailsResponse details(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return ExceptionReportDetailsResponse.from(service.findDetails(user, id));
    }

    @RequirePermission("EXCEPTION_REPORT_HANDLE")
    @PostMapping("/{id}/handle")
    public ExceptionReportResponse handle(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id, @Valid @RequestBody HandleExceptionReportRequest request) {
        return ExceptionReportResponse.from(service.handle(user, id, request.toCommand()));
    }
}
