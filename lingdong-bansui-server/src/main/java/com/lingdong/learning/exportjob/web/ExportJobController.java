package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.exportjob.application.ExportJobApplicationService;
import com.lingdong.learning.exportjob.application.ExportJobOptionService;
import com.lingdong.learning.exportjob.application.ExportJobQueryService;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/** Web 端异步导出创建、本人台账、详情和受控下载 API。 */
@RestController
@RequestMapping("/api/v1/export-jobs")
public class ExportJobController {
    private final ExportJobApplicationService applicationService;
    private final ExportJobQueryService queryService;
    private final ExportJobOptionService optionService;

    public ExportJobController(
            ExportJobApplicationService applicationService,
            ExportJobQueryService queryService,
            ExportJobOptionService optionService
    ) {
        this.applicationService = applicationService;
        this.queryService = queryService;
        this.optionService = optionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExportJobResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateExportJobRequest request,
            HttpServletRequest servletRequest
    ) {
        return ExportJobResponse.from(applicationService.create(
                request.toCommand(currentUser.userId(), servletRequest.getRemoteAddr())));
    }

    @RequirePermission("EXPORT_JOB_READ")
    @GetMapping
    public ExportJobPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) ExportJobType exportType,
            @RequestParam(required = false) ExportJobStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ExportJobPageResponse.from(queryService.findPage(
                currentUser.userId(), exportType, status, page, pageSize));
    }

    @RequirePermission("EXPORT_JOB_READ")
    @GetMapping("/options")
    public ExportJobOptionsResponse options(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam ExportJobType exportType
    ) {
        return ExportJobOptionsResponse.from(
                optionService.findOptions(currentUser.userId(), exportType));
    }

    @RequirePermission("EXPORT_JOB_READ")
    @GetMapping("/{id}")
    public ExportJobDetailResponse detail(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        return ExportJobDetailResponse.from(queryService.findDetail(currentUser.userId(), id));
    }

    @RequirePermission("EXPORT_JOB_READ")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        AttachmentContentView content = queryService.download(currentUser.userId(), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(safeMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.content().length)
                .body(content.content());
    }

    private MediaType safeMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
