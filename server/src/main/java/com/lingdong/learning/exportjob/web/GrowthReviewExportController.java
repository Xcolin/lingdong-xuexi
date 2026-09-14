package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.exportjob.application.GrowthReviewExportApplicationService;
import com.lingdong.learning.exportjob.application.GrowthReviewExportHistoryService;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Web 家长的复盘导出创建及历史入口，响应复用统一作业公开字段。 */
@RestController
@RequestMapping("/api/v1/growth-review-export-jobs")
public class GrowthReviewExportController {
    private final GrowthReviewExportApplicationService application;
    private final GrowthReviewExportHistoryService history;
    public GrowthReviewExportController(GrowthReviewExportApplicationService application,
            GrowthReviewExportHistoryService history) {
        this.application = application;
        this.history = history;
    }

    @GetMapping
    @RequirePermission("EXPORT_JOB_READ")
    public ExportJobPageResponse list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam Long studentId, @RequestParam(required = false) ExportJobStatus status,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ExportJobPageResponse.from(history.findPage(user, studentId, status, page, pageSize));
    }

    @GetMapping("/{id}")
    @RequirePermission("EXPORT_JOB_READ")
    public ExportJobResponse detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ExportJobResponse.from(history.findDetail(user, id));
    }

    @GetMapping("/{id}/download")
    @RequirePermission("EXPORT_JOB_READ")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        var content = history.download(user, id);
        MediaType type = "application/pdf".equals(content.contentType()) ? MediaType.APPLICATION_PDF
                : "application/zip".equals(content.contentType()) ? MediaType.parseMediaType("application/zip")
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.originalName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(content.content().length).body(content.content());
    }

    @PostMapping
    @RequirePermission("EXPORT_JOB_CREATE")
    @ResponseStatus(HttpStatus.CREATED)
    public ExportJobResponse create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateGrowthReviewExportRequest request, HttpServletRequest servletRequest) {
        return ExportJobResponse.from(application.create(user, request.selection(), Long.valueOf(request.templateId()),
                request.mode(), request.reason(), servletRequest.getRemoteAddr()));
    }
}
