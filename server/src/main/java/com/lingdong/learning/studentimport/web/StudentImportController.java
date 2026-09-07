package com.lingdong.learning.studentimport.web;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.studentimport.application.StudentImportApplicationService;
import com.lingdong.learning.studentimport.application.StudentImportCredentialService;
import com.lingdong.learning.studentimport.application.StudentImportQueryService;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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

/** Web 端机构学员批量导入执行、结果、重试和一次性凭证 API。 */
@RestController
@RequestMapping("/api/v1/student-import-executions")
public class StudentImportController {
    private final StudentImportApplicationService applicationService;
    private final StudentImportQueryService queryService;
    private final StudentImportCredentialService credentialService;

    public StudentImportController(
            StudentImportApplicationService applicationService,
            StudentImportQueryService queryService,
            StudentImportCredentialService credentialService
    ) {
        this.applicationService = applicationService;
        this.queryService = queryService;
        this.credentialService = credentialService;
    }

    @RequirePermission("STUDENT_IMPORT_EXECUTE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentImportResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateStudentImportRequest request
    ) {
        return StudentImportResponse.from(
                applicationService.create(request.toCommand(currentUser.userId())));
    }

    @RequirePermission("STUDENT_IMPORT_RESULT_READ")
    @GetMapping
    public StudentImportPageResponse list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) StudentImportExecutionStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return StudentImportPageResponse.from(
                queryService.findPage(currentUser.userId(), status, page, pageSize));
    }

    @RequirePermission("STUDENT_IMPORT_RESULT_READ")
    @GetMapping("/{id}")
    public StudentImportResponse detail(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        return StudentImportResponse.from(queryService.findDetail(currentUser.userId(), id));
    }

    @RequirePermission("STUDENT_IMPORT_RESULT_READ")
    @GetMapping("/{id}/rows")
    public StudentImportRowPageResponse rows(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestParam(required = false) StudentImportRowStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return StudentImportRowPageResponse.from(
                queryService.findRows(currentUser.userId(), id, status, page, pageSize));
    }

    @RequirePermission("STUDENT_IMPORT_EXECUTE")
    @PostMapping("/{id}/retry-failures")
    public StudentImportResponse retryFailures(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        return StudentImportResponse.from(
                applicationService.retryFailures(currentUser.userId(), id));
    }

    @RequirePermission("STUDENT_IMPORT_CREDENTIAL_DOWNLOAD")
    @GetMapping("/{id}/credentials")
    public ResponseEntity<byte[]> credentials(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        AttachmentContentView content = credentialService.download(currentUser.userId(), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.content().length)
                .body(content.content());
    }
}
