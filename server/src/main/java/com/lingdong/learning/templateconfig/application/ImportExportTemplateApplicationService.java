package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 管理导入导出模板的不可变版本、唯一默认项和启停生命周期。 */
@Service
public class ImportExportTemplateApplicationService {
    private static final String READ_PERMISSION = "IMPORT_EXPORT_TEMPLATE_READ";
    private static final String MANAGE_PERMISSION = "IMPORT_EXPORT_TEMPLATE_MANAGE";
    private static final String TYPE_DICTIONARY = "IMPORT_EXPORT_TEMPLATE_TYPE";
    private static final String MODULE_DICTIONARY = "IMPORT_EXPORT_TEMPLATE_MODULE";
    private static final String STATUS_DICTIONARY = "IMPORT_EXPORT_TEMPLATE_STATUS";
    private static final String DEFAULT_SCOPE_KEY = "DEFAULT";
    private static final String ATTACHMENT_MODULE = "IMPORT_EXPORT_TEMPLATE";
    private static final String ATTACHMENT_CATEGORY = "TEMPLATE_FILE";
    private static final String ATTACHMENT_RELATION_TYPE = "TEMPLATE_FILE";
    private static final String ATTACHMENT_VISIBLE_SCOPE = "SYSTEM_CONFIGURATION";

    private final ImportExportTemplateMapper templateMapper;
    private final ManagedFileMapper fileMapper;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final PermissionDecisionService permissionDecisionService;
    private final IdGenerator idGenerator;
    private final ManagedAttachmentContentService contentService;
    private final AttachmentFileApplicationService fileService;
    private final ImportTemplateFieldApplicationService fieldService;

    public ImportExportTemplateApplicationService(
            ImportExportTemplateMapper templateMapper,
            ManagedFileMapper fileMapper,
            DictionaryItemMapper dictionaryItemMapper,
            PermissionDecisionService permissionDecisionService,
            IdGenerator idGenerator,
            ManagedAttachmentContentService contentService,
            AttachmentFileApplicationService fileService,
            ImportTemplateFieldApplicationService fieldService
    ) {
        this.templateMapper = templateMapper;
        this.fileMapper = fileMapper;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.permissionDecisionService = permissionDecisionService;
        this.idGenerator = idGenerator;
        this.contentService = contentService;
        this.fileService = fileService;
        this.fieldService = fieldService;
    }

    /** 创建一个不可变模板版本；设为默认时在同一事务内撤销原默认项。 */
    @Transactional
    public ImportExportTemplate createTemplate(CreateImportExportTemplateCommand command) {
        Objects.requireNonNull(command, "模板创建请求不能为空");
        ValidatedTemplateMetadata metadata = validateMetadata(
                command.operatorId(), command.templateName(), command.templateType(),
                command.moduleCode(), command.version()
        );
        ManagedFileRecord file = requireAvailableFile(command.fileId());
        return persistTemplate(metadata, file, command.defaultTemplate());
    }

    /** 上传文件并原子创建模板版本及其业务关系。 */
    @Transactional
    public ImportExportTemplate createTemplate(CreateImportExportTemplateUploadCommand command) {
        Objects.requireNonNull(command, "模板上传创建请求不能为空");
        ValidatedTemplateMetadata metadata = validateMetadata(
                command.operatorId(), command.templateName(), command.templateType(),
                command.moduleCode(), command.version()
        );
        fieldService.validateForNewTemplate(metadata.templateType(), command.fields());
        ManagedFile storedFile = contentService.store(
                command.operatorId(), ATTACHMENT_MODULE, ATTACHMENT_CATEGORY,
                command.originalName(), command.contentType(), command.content()
        );
        try {
            requireOwnedTemplateFile(command.operatorId(), storedFile);
            ImportExportTemplate template = persistTemplate(
                    metadata, toRecord(storedFile), command.defaultTemplate(), command.fields()
            );
            fileService.attachToBusiness(new AttachFileToBusinessCommand(
                    storedFile.id(), ATTACHMENT_MODULE, template.id(),
                    ATTACHMENT_RELATION_TYPE, ATTACHMENT_VISIBLE_SCOPE
            ));
            return template;
        } catch (RuntimeException exception) {
            discardContentPreservingFailure(storedFile.storageKey(), exception);
            throw exception;
        }
    }

    /** 下载历史或当前模板文件；模板停用不影响审计和复用所需的读取能力。 */
    @Transactional(readOnly = true)
    public ImportExportTemplateContent downloadTemplate(Long operatorId, Long templateId) {
        requirePermission(operatorId, READ_PERMISSION);
        ImportExportTemplateRecord template = requireTemplate(templateId);
        AttachmentContentView content = contentService.read(template.fileId());
        return new ImportExportTemplateContent(
                content.originalName(), content.contentType(), content.content()
        );
    }

    private ImportExportTemplate persistTemplate(
            ValidatedTemplateMetadata metadata,
            ManagedFileRecord file,
            boolean defaultTemplate
    ) {
        return persistTemplate(metadata, file, defaultTemplate, null);
    }

    private ImportExportTemplate persistTemplate(
            ValidatedTemplateMetadata metadata,
            ManagedFileRecord file,
            boolean defaultTemplate,
            List<ImportTemplateFieldInput> fields
    ) {
        long templateId = idGenerator.nextId();
        if (defaultTemplate) {
            templateMapper.lockGroup(metadata.moduleCode(), metadata.templateType());
            templateMapper.clearCurrentDefault(metadata.moduleCode(), metadata.templateType());
        }

        ImportExportTemplateRecord record = new ImportExportTemplateRecord(
                templateId,
                metadata.templateName(),
                metadata.templateType(),
                metadata.moduleCode(),
                metadata.version(),
                file.id(),
                defaultTemplate,
                defaultTemplate ? DEFAULT_SCOPE_KEY : nonDefaultScopeKey(templateId),
                ImportExportTemplateStatus.ENABLED,
                0L,
                null,
                null,
                file.originalName(),
                file.contentType(),
                file.sizeBytes()
        );
        if (templateMapper.insert(record) != 1) {
            throw new IllegalStateException("模板保存失败");
        }
        if (fields != null) {
            fieldService.insertForNewTemplate(templateId, metadata.templateType(), fields);
        }
        return toTemplate(requireTemplate(record.id()));
    }

    private ValidatedTemplateMetadata validateMetadata(
            Long operatorId,
            String templateName,
            TemplateType templateType,
            String moduleCode,
            String version
    ) {
        requirePermission(operatorId, MANAGE_PERMISSION);
        String normalizedName = requiredText(templateName, "模板名称", 100);
        TemplateType normalizedType = requireTemplateType(templateType);
        requireEnabledOption(TYPE_DICTIONARY, normalizedType.name(), "模板类型");
        String normalizedModule = requiredCode(moduleCode, "适用模块");
        requireEnabledOption(MODULE_DICTIONARY, normalizedModule, "适用模块");
        String normalizedVersion = requiredText(version, "模板版本", 32);
        if (templateMapper.findByModuleTypeAndVersion(
                normalizedModule, normalizedType, normalizedVersion) != null) {
            throw new IllegalStateException("模板版本已存在："
                    + normalizedModule + "/" + normalizedType + "/" + normalizedVersion);
        }
        return new ValidatedTemplateMetadata(
                normalizedName, normalizedType, normalizedModule, normalizedVersion
        );
    }

    private ManagedFileRecord toRecord(ManagedFile file) {
        return new ManagedFileRecord(
                file.id(), file.storageKey(), file.originalName(), file.extension(), file.contentType(),
                file.sizeBytes(), file.uploaderId(), file.moduleCode(), file.fileCategory(),
                file.contentSha256(), file.status(), null, null, null
        );
    }

    private void requireOwnedTemplateFile(Long operatorId, ManagedFile file) {
        if (!Objects.equals(operatorId, file.uploaderId())) {
            throw new IllegalStateException("模板文件上传人与当前操作人不一致");
        }
        if (!ATTACHMENT_MODULE.equals(file.moduleCode())
                || !ATTACHMENT_CATEGORY.equals(file.fileCategory())
                || file.status() != FileStatus.AVAILABLE) {
            throw new IllegalStateException("模板文件未按统一附件规则完成保存");
        }
    }

    private void discardContentPreservingFailure(String storageKey, RuntimeException originalFailure) {
        try {
            contentService.discardContent(storageKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    /** 查询模板配置台账，所有条件均可选且最多返回 200 条。 */
    public List<ImportExportTemplate> listTemplates(ImportExportTemplateQuery query) {
        Objects.requireNonNull(query, "模板查询条件不能为空");
        requirePermission(query.operatorId(), READ_PERMISSION);
        String templateName = optionalText(query.templateName(), 100);
        String moduleCode = optionalCode(query.moduleCode());
        return templateMapper.findAll(templateName, query.templateType(), moduleCode, query.status())
                .stream().map(this::toTemplate).toList();
    }

    /** 返回模板页面可以使用的当前启用字典选项。 */
    public ImportExportTemplateOptions findOptions(Long operatorId) {
        requirePermission(operatorId, READ_PERMISSION);
        return new ImportExportTemplateOptions(
                options(TYPE_DICTIONARY),
                options(MODULE_DICTIONARY),
                options(STATUS_DICTIONARY)
        );
    }

    /** 将启用模板切换为同模块同类型的唯一默认模板。 */
    @Transactional
    public ImportExportTemplate setDefaultTemplate(Long operatorId, Long templateId, Long versionNo) {
        requirePermission(operatorId, MANAGE_PERMISSION);
        Long expectedVersion = requiredVersion(versionNo);
        ImportExportTemplateRecord current = requireTemplate(templateId);
        requireExpectedVersion(current, expectedVersion);
        if (current.status() != ImportExportTemplateStatus.ENABLED) {
            throw new IllegalStateException("模板已停用，不能设为默认模板：" + current.id());
        }
        if (Boolean.TRUE.equals(current.defaultTemplate())) {
            return toTemplate(current);
        }

        List<ImportExportTemplateRecord> group =
                templateMapper.lockGroup(current.moduleCode(), current.templateType());
        current = group.stream().filter(template -> template.id().equals(templateId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("模板不存在：" + templateId));
        requireExpectedVersion(current, expectedVersion);
        templateMapper.clearCurrentDefault(current.moduleCode(), current.templateType());
        if (templateMapper.markAsDefault(current.id(), expectedVersion) != 1) {
            throw versionConflict(current.id());
        }
        return toTemplate(requireTemplate(current.id()));
    }

    /** 停用模板并撤销默认标记。 */
    @Transactional
    public ImportExportTemplate disableTemplate(Long operatorId, Long templateId, Long versionNo) {
        return changeStatus(operatorId, templateId, versionNo, ImportExportTemplateStatus.DISABLED);
    }

    /** 重新启用模板；启用操作不会自动设置默认项。 */
    @Transactional
    public ImportExportTemplate enableTemplate(Long operatorId, Long templateId, Long versionNo) {
        return changeStatus(operatorId, templateId, versionNo, ImportExportTemplateStatus.ENABLED);
    }

    /** 按读取权限查询单个模板的安全元数据。 */
    public ImportExportTemplate findTemplate(Long operatorId, Long templateId) {
        requirePermission(operatorId, READ_PERMISSION);
        return toTemplate(requireTemplate(templateId));
    }

    /** 供后续导入导出任务使用，只返回启用中的当前默认模板。 */
    public ImportExportTemplate findCurrentDefault(String moduleCode, TemplateType templateType) {
        ImportExportTemplateRecord template = templateMapper.findCurrentDefault(
                requiredCode(moduleCode, "适用模块"), requireTemplateType(templateType)
        );
        return template == null ? null : toTemplate(template);
    }

    private ImportExportTemplate changeStatus(
            Long operatorId,
            Long templateId,
            Long versionNo,
            ImportExportTemplateStatus targetStatus
    ) {
        requirePermission(operatorId, MANAGE_PERMISSION);
        Long expectedVersion = requiredVersion(versionNo);
        ImportExportTemplateRecord current = requireTemplate(templateId);
        requireExpectedVersion(current, expectedVersion);
        if (current.status() == targetStatus) {
            return toTemplate(current);
        }
        int changed = targetStatus == ImportExportTemplateStatus.ENABLED
                ? enableTemplate(current, expectedVersion)
                : templateMapper.disable(current.id(), expectedVersion);
        if (changed != 1) {
            throw versionConflict(current.id());
        }
        return toTemplate(requireTemplate(current.id()));
    }

    private int enableTemplate(ImportExportTemplateRecord current, Long expectedVersion) {
        if (current.templateType() == TemplateType.IMPORT && !fieldService.hasFields(current.id())) {
            throw new IllegalStateException("导入模板尚未配置字段映射，不能启用：" + current.id());
        }
        return templateMapper.enable(current.id(), expectedVersion);
    }

    private ImportExportTemplateRecord requireTemplate(Long templateId) {
        if (templateId == null) {
            throw new IllegalArgumentException("模板标识不能为空");
        }
        ImportExportTemplateRecord template = templateMapper.findById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在：" + templateId);
        }
        return template;
    }

    private ManagedFileRecord requireAvailableFile(Long fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("模板附件不能为空");
        }
        ManagedFileRecord file = fileMapper.findById(fileId);
        if (file == null) {
            throw new IllegalArgumentException("模板附件不存在：" + fileId);
        }
        if (file.status() != FileStatus.AVAILABLE) {
            throw new IllegalStateException("模板附件未完成，不能创建模板：" + fileId);
        }
        return file;
    }

    private TemplateType requireTemplateType(TemplateType templateType) {
        if (templateType == null) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        return templateType;
    }

    private void requireEnabledOption(String typeCode, String itemCode, String fieldName) {
        boolean enabled = dictionaryItemMapper.findEnabledByTypeCode(typeCode).stream()
                .anyMatch(item -> item.code().equals(itemCode));
        if (!enabled) {
            throw new IllegalArgumentException(fieldName + "未启用或不存在：" + itemCode);
        }
    }

    private List<ImportExportTemplateOption> options(String typeCode) {
        return dictionaryItemMapper.findEnabledByTypeCode(typeCode).stream()
                .map(this::toOption)
                .toList();
    }

    private ImportExportTemplateOption toOption(DictionaryItem item) {
        return new ImportExportTemplateOption(item.code(), item.name(), item.defaultItem());
    }

    private void requirePermission(Long userId, String permissionCode) {
        if (!permissionDecisionService.isAllowed(userId, PermissionClient.WEB, permissionCode)) {
            throw new SystemOperationAccessDeniedException(
                    "当前账号缺少导入导出模板管理权限：" + permissionCode);
        }
    }

    private void requireExpectedVersion(ImportExportTemplateRecord current, Long expectedVersion) {
        if (!current.versionNo().equals(expectedVersion)) {
            throw versionConflict(current.id());
        }
    }

    private Long requiredVersion(Long versionNo) {
        if (versionNo == null || versionNo < 0) {
            throw new IllegalArgumentException("模板版本号必须为非负整数");
        }
        return versionNo;
    }

    private IllegalStateException versionConflict(Long templateId) {
        return new IllegalStateException("模板版本已变化，请刷新后重试：" + templateId);
    }

    private String optionalCode(String value) {
        String normalized = optionalText(value, 64);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("文本长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String requiredCode(String value, String fieldName) {
        return requiredText(value, fieldName, 64).toUpperCase(Locale.ROOT);
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalizedValue;
    }

    private String nonDefaultScopeKey(long templateId) {
        return "ID:" + templateId;
    }

    private ImportExportTemplate toTemplate(ImportExportTemplateRecord record) {
        return new ImportExportTemplate(
                record.id(),
                record.templateName(),
                record.templateType(),
                record.moduleCode(),
                record.version(),
                record.fileId(),
                record.fileName(),
                record.contentType(),
                record.sizeBytes(),
                Boolean.TRUE.equals(record.defaultTemplate()),
                record.status(),
                record.versionNo(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private record ValidatedTemplateMetadata(
            String templateName,
            TemplateType templateType,
            String moduleCode,
            String version
    ) { }
}
