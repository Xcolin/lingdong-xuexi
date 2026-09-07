package com.lingdong.learning.importjob.web;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.importjob.application.CreateImportJobCommand;
import com.lingdong.learning.importjob.application.ImportJobApplicationService;
import com.lingdong.learning.importjob.application.ImportJobOptionService;
import com.lingdong.learning.importjob.application.ImportJobQuery;
import com.lingdong.learning.importjob.application.ImportJobQueryService;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Web 端通用 XLSX 导入校验作业 API。 */
@RestController
@RequestMapping("/api/v1/import-jobs")
public class ImportJobController {
    private final ImportJobApplicationService applicationService;
    private final ImportJobQueryService queryService;
    private final ImportJobOptionService optionService;

    public ImportJobController(
            ImportJobApplicationService applicationService,
            ImportJobQueryService queryService,
            ImportJobOptionService optionService
    ) {
        this.applicationService = applicationService;
        this.queryService = queryService;
        this.optionService = optionService;
    }

    @RequirePermission("IMPORT_JOB_CREATE")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportJobResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam Long templateId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam MultipartFile file
    ) {
        try {
            return ImportJobResponse.from(applicationService.create(new CreateImportJobCommand(
                    currentUser.userId(), templateId, organizationId, file.getOriginalFilename(),
                    file.getContentType(), file.getBytes())));
        } catch (IOException exception) {
            throw new IllegalArgumentException("导入校验文件无法读取", exception);
        }
    }

    @RequirePermission("IMPORT_JOB_READ")
    @GetMapping
    public ImportJobPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String jobCode,
            @RequestParam(required = false) Long templateId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) ImportJobStatus status,
            @RequestParam(required = false) LocalDateTime queuedFrom,
            @RequestParam(required = false) LocalDateTime queuedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ImportJobPageResponse.from(queryService.findPage(new ImportJobQuery(
                currentUser.userId(), jobCode, templateId, organizationId, status,
                queuedFrom, queuedTo, page, pageSize)));
    }

    @RequirePermission("IMPORT_JOB_READ")
    @GetMapping("/options")
    public ImportJobOptionsResponse options(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ImportJobOptionsResponse.from(optionService.findOptions(currentUser.userId()));
    }

    @RequirePermission("IMPORT_JOB_READ")
    @GetMapping("/{id}")
    public ImportJobDetailResponse detail(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return ImportJobDetailResponse.from(queryService.findDetail(currentUser.userId(), id));
    }

    @RequirePermission("IMPORT_JOB_READ")
    @GetMapping("/{id}/errors")
    public ImportJobErrorPageResponse errors(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ImportJobErrorPageResponse.from(
                queryService.findErrors(currentUser.userId(), id, page, pageSize));
    }

    @RequirePermission("IMPORT_JOB_READ")
    @GetMapping("/{id}/source-file")
    public ResponseEntity<byte[]> sourceFile(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return download(queryService.readSource(currentUser.userId(), id));
    }

    @RequirePermission("IMPORT_JOB_READ")
    @GetMapping("/{id}/error-file")
    public ResponseEntity<byte[]> errorFile(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return download(queryService.readErrorFile(currentUser.userId(), id));
    }

    private ResponseEntity<byte[]> download(AttachmentContentView content) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        byte[] body = content.content();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(body.length).body(body);
    }
}
