package com.lingdong.learning.importjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateFieldRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateFieldMapper;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 创建导入校验作业并固化模板、字典、附件和请求范围事实。 */
@Service
public class ImportJobApplicationService {
    private static final String ATTACHMENT_MODULE = "IMPORT_JOB";
    private static final String ATTACHMENT_CATEGORY = "IMPORT_VALIDATION";

    private final ImportJobMapper jobMapper;
    private final ImportExportTemplateMapper templateMapper;
    private final ImportExportTemplateFieldMapper fieldMapper;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final ImportJobAccessService accessService;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final ManagedAttachmentContentService contentService;
    private final AttachmentFileApplicationService fileService;

    public ImportJobApplicationService(
            ImportJobMapper jobMapper,
            ImportExportTemplateMapper templateMapper,
            ImportExportTemplateFieldMapper fieldMapper,
            DictionaryItemMapper dictionaryItemMapper,
            ImportJobAccessService accessService,
            IdGenerator idGenerator,
            ObjectMapper objectMapper,
            ManagedAttachmentContentService contentService,
            AttachmentFileApplicationService fileService
    ) {
        this.jobMapper = jobMapper;
        this.templateMapper = templateMapper;
        this.fieldMapper = fieldMapper;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.accessService = accessService;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.contentService = contentService;
        this.fileService = fileService;
    }

    /** 原子创建只执行校验、不写业务数据的排队作业。 */
    @Transactional
    public ImportJobView create(CreateImportJobCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("导入校验作业创建请求不能为空");
        }
        validateFile(command.originalName(), command.content());
        accessService.requireCreate(command.operatorId(), command.organizationId());
        ImportExportTemplateRecord template = requireUsableTemplate(command.templateId());
        List<ImportExportTemplateFieldRecord> fields = fieldMapper.findByTemplateId(template.id());
        if (fields.isEmpty()) {
            throw new IllegalStateException("导入模板尚未配置字段映射：" + template.id());
        }
        String snapshot = serializeSnapshot(fields);
        ManagedFile sourceFile = contentService.store(
                command.operatorId(), ATTACHMENT_MODULE, ATTACHMENT_CATEGORY,
                command.originalName(), command.contentType(), command.content()
        );
        try {
            long id = idGenerator.nextId();
            LocalDateTime now = LocalDateTime.now();
            ImportJobRecord record = new ImportJobRecord(
                    id, "IMP-" + id, template.id(), template.version(), template.templateName(), snapshot,
                    sourceFile.id(), null, command.operatorId(), command.organizationId(),
                    ImportJobStatus.QUEUED, 0L, null, null, 0, 0, 0, 0,
                    now, null, null, now, now
            );
            if (jobMapper.insert(record) != 1) {
                throw new IllegalStateException("导入校验作业保存失败");
            }
            fileService.attachToBusiness(new AttachFileToBusinessCommand(
                    sourceFile.id(), ATTACHMENT_MODULE, id,
                    "IMPORT_JOB_SOURCE", "IMPORT_VALIDATION"
            ));
            return toView(record);
        } catch (RuntimeException exception) {
            discardPreservingFailure(sourceFile.storageKey(), exception);
            throw exception;
        }
    }

    private ImportExportTemplateRecord requireUsableTemplate(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("导入模板标识不能为空");
        }
        ImportExportTemplateRecord template = templateMapper.findById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("导入模板不存在：" + templateId);
        }
        if (template.templateType() != TemplateType.IMPORT
                || template.status() != ImportExportTemplateStatus.ENABLED) {
            throw new IllegalStateException("只能使用启用中的导入模板创建校验作业");
        }
        return template;
    }

    private String serializeSnapshot(List<ImportExportTemplateFieldRecord> fields) {
        List<ImportFieldMappingSnapshot> snapshot = fields.stream()
                .sorted(Comparator.comparing(ImportExportTemplateFieldRecord::sortOrder))
                .map(field -> new ImportFieldMappingSnapshot(
                        field.fieldCode(), field.columnName(), field.dataType(),
                        Boolean.TRUE.equals(field.required()), field.maxLength(),
                        dictionaryValues(field.dictionaryTypeCode()), field.sortOrder()
                )).toList();
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导入字段映射快照生成失败", exception);
        }
    }

    private Set<String> dictionaryValues(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return Set.of();
        }
        return dictionaryItemMapper.findEnabledByTypeCode(typeCode).stream()
                .map(item -> item.code().trim())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void validateFile(String originalName, byte[] content) {
        if (originalName == null || originalName.isBlank()
                || !originalName.trim().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("导入校验文件必须使用 .xlsx 扩展名");
        }
        if (originalName.length() > 255 || originalName.contains("/") || originalName.contains("\\")) {
            throw new IllegalArgumentException("导入校验文件名不合法");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("导入校验文件不能为空");
        }
    }

    private void discardPreservingFailure(String storageKey, RuntimeException originalFailure) {
        try {
            contentService.discardContent(storageKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private ImportJobView toView(ImportJobRecord job) {
        return ImportJobView.from(job);
    }
}
