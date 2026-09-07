package com.lingdong.learning.templateconfig.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.templateconfig.application.CreateImportExportTemplateUploadCommand;
import com.lingdong.learning.templateconfig.application.ImportExportTemplateApplicationService;
import com.lingdong.learning.templateconfig.application.ImportExportTemplateContent;
import com.lingdong.learning.templateconfig.application.ImportExportTemplateQuery;
import com.lingdong.learning.templateconfig.application.ImportTemplateFieldInput;
import com.lingdong.learning.templateconfig.application.ImportTemplateFieldApplicationService;
import com.lingdong.learning.templateconfig.application.ReplaceImportTemplateFieldsCommand;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 提供受双功能开关和动态 RBAC 共同保护的导入导出模板管理 API。 */
@RestController
@RequestMapping("/api/v1/import-export-templates")
public class ImportExportTemplateManagementController {
    private static final String TEMPLATE_FEATURE = "IMPORT_EXPORT_TEMPLATE_MANAGEMENT";
    private static final String ATTACHMENT_FEATURE = "ATTACHMENT_SERVICE";

    private final ImportExportTemplateApplicationService templateService;
    private final FeatureAccessService featureAccessService;
    private final ObjectMapper objectMapper;
    private final ImportTemplateFieldApplicationService fieldService;

    public ImportExportTemplateManagementController(
            ImportExportTemplateApplicationService templateService,
            FeatureAccessService featureAccessService,
            ObjectMapper objectMapper,
            ImportTemplateFieldApplicationService fieldService
    ) {
        this.templateService = templateService;
        this.featureAccessService = featureAccessService;
        this.objectMapper = objectMapper;
        this.fieldService = fieldService;
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")
    @GetMapping("/options")
    public ImportExportTemplateOptionsResponse options(
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        requireFeatures();
        return ImportExportTemplateOptionsResponse.from(
                templateService.findOptions(currentUser.userId()));
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")
    @GetMapping
    public List<ImportExportTemplateResponse> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String templateName,
            @RequestParam(required = false) TemplateType templateType,
            @RequestParam(required = false) String moduleCode,
            @RequestParam(required = false) ImportExportTemplateStatus status
    ) {
        requireFeatures();
        return templateService.listTemplates(new ImportExportTemplateQuery(
                        currentUser.userId(), templateName, templateType, moduleCode, status))
                .stream().map(ImportExportTemplateResponse::from).toList();
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportExportTemplateResponse create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam String templateName,
            @RequestParam TemplateType templateType,
            @RequestParam String moduleCode,
            @RequestParam String version,
            @RequestParam(defaultValue = "false") boolean defaultTemplate,
            @RequestParam(required = false) String fields,
            @RequestParam MultipartFile file
    ) {
        requireFeatures();
        try {
            return ImportExportTemplateResponse.from(templateService.createTemplate(
                    new CreateImportExportTemplateUploadCommand(
                            currentUser.userId(), templateName, templateType, moduleCode, version,
                            file.getOriginalFilename(), file.getContentType(), file.getBytes(), defaultTemplate,
                            parseFields(fields)
                    )));
        } catch (IOException exception) {
            throw new IllegalArgumentException("上传模板文件无法读取", exception);
        }
    }

    private List<ImportTemplateFieldInput> parseFields(String fields) {
        if (fields == null || fields.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(fields, new TypeReference<>() { });
        } catch (IOException exception) {
            throw new IllegalArgumentException("导入模板字段映射格式不正确", exception);
        }
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")
    @GetMapping("/{id}/fields")
    public List<ImportTemplateFieldResponse> fields(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        requireFeatures();
        return fieldService.findByTemplate(currentUser.userId(), id).stream()
                .map(ImportTemplateFieldResponse::from)
                .toList();
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")
    @PutMapping("/{id}/fields")
    public ReplaceImportTemplateFieldsResponse replaceFields(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @RequestBody ReplaceImportTemplateFieldsRequest request
    ) {
        requireFeatures();
        if (request == null || request.fields() == null) {
            throw new IllegalArgumentException("字段映射替换请求不能为空");
        }
        List<ImportTemplateFieldInput> inputs = request.fields().stream()
                .map(field -> {
                    if (field == null) {
                        throw new IllegalArgumentException("字段映射项不能为空");
                    }
                    return field.toInput();
                })
                .toList();
        List<ImportTemplateFieldResponse> fields = fieldService.replace(
                        new ReplaceImportTemplateFieldsCommand(
                                currentUser.userId(), id, request.versionNo(), inputs))
                .stream().map(ImportTemplateFieldResponse::from).toList();
        return new ReplaceImportTemplateFieldsResponse(request.versionNo() + 1, fields);
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")
    @PostMapping("/{id}/enable")
    public ImportExportTemplateResponse enable(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ImportExportTemplateVersionRequest request
    ) {
        requireFeatures();
        return ImportExportTemplateResponse.from(
                templateService.enableTemplate(currentUser.userId(), id, request.versionNo()));
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")
    @PostMapping("/{id}/disable")
    public ImportExportTemplateResponse disable(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ImportExportTemplateVersionRequest request
    ) {
        requireFeatures();
        return ImportExportTemplateResponse.from(
                templateService.disableTemplate(currentUser.userId(), id, request.versionNo()));
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_MANAGE")
    @PostMapping("/{id}/default")
    public ImportExportTemplateResponse setDefault(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody ImportExportTemplateVersionRequest request
    ) {
        requireFeatures();
        return ImportExportTemplateResponse.from(
                templateService.setDefaultTemplate(currentUser.userId(), id, request.versionNo()));
    }

    @RequirePermission("IMPORT_EXPORT_TEMPLATE_READ")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id
    ) {
        requireFeatures();
        ImportExportTemplateContent content = templateService.downloadTemplate(currentUser.userId(), id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.originalName(), StandardCharsets.UTF_8)
                .build();
        byte[] body = content.content();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(body.length)
                .body(body);
    }

    private void requireFeatures() {
        featureAccessService.requireEnabled(TEMPLATE_FEATURE, null);
        featureAccessService.requireEnabled(ATTACHMENT_FEATURE, null);
    }
}
