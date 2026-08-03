package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

/**
 * 管理导入导出模板的版本、默认项和启停状态。
 * 模板文件只引用附件模块已完成的元数据，不在此服务处理文件上传或具体导入导出任务。
 */
@Service
public class ImportExportTemplateApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final String DEFAULT_SCOPE_KEY = "DEFAULT";

    private final ImportExportTemplateMapper templateMapper;
    private final ManagedFileMapper fileMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdGenerator idGenerator;

    public ImportExportTemplateApplicationService(
            ImportExportTemplateMapper templateMapper,
            ManagedFileMapper fileMapper,
            UserRoleMapper userRoleMapper,
            IdGenerator idGenerator
    ) {
        this.templateMapper = templateMapper;
        this.fileMapper = fileMapper;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
    }

    /** 创建一个启用模板；请求设为默认时会撤销同模块同类型的原默认模板。 */
    @Transactional
    public ImportExportTemplate createTemplate(CreateImportExportTemplateCommand command) {
        Objects.requireNonNull(command, "模板创建请求不能为空");
        requireSystemAdmin(command.operatorId());
        String templateName = requiredText(command.templateName(), "模板名称", 100);
        TemplateType templateType = requireTemplateType(command.templateType());
        String moduleCode = requiredCode(command.moduleCode(), "模块编码");
        String version = requiredText(command.version(), "模板版本", 32);
        requireAvailableFile(command.fileId());
        if (templateMapper.findByModuleTypeAndVersion(moduleCode, templateType, version) != null) {
            throw new IllegalStateException("模板版本已存在：" + moduleCode + "/" + templateType + "/" + version);
        }

        long templateId = idGenerator.nextId();
        if (command.defaultTemplate()) {
            templateMapper.clearCurrentDefault(moduleCode, templateType);
        }
        ImportExportTemplateRecord record = new ImportExportTemplateRecord(
                templateId,
                templateName,
                templateType,
                moduleCode,
                version,
                command.fileId(),
                command.defaultTemplate(),
                command.defaultTemplate() ? DEFAULT_SCOPE_KEY : nonDefaultScopeKey(templateId),
                ImportExportTemplateStatus.ENABLED,
                null,
                null
        );
        if (templateMapper.insert(record) != 1) {
            throw new IllegalStateException("模板保存失败");
        }
        return toTemplate(record);
    }

    /** 将已启用模板切换为当前默认模板。 */
    @Transactional
    public void setDefaultTemplate(Long operatorId, Long templateId) {
        requireSystemAdmin(operatorId);
        ImportExportTemplateRecord template = requireTemplate(templateId);
        if (template.status() != ImportExportTemplateStatus.ENABLED) {
            throw new IllegalStateException("模板已停用，不能设为默认模板：" + template.id());
        }
        if (Boolean.TRUE.equals(template.defaultTemplate())) {
            return;
        }
        templateMapper.clearCurrentDefault(template.moduleCode(), template.templateType());
        if (templateMapper.markAsDefault(template.id()) != 1) {
            throw new IllegalStateException("默认模板切换失败");
        }
    }

    /** 停用模板，同时撤销其默认标记，使后续任务无法继续选用该模板。 */
    @Transactional
    public void disableTemplate(Long operatorId, Long templateId) {
        requireSystemAdmin(operatorId);
        ImportExportTemplateRecord template = requireTemplate(templateId);
        if (template.status() == ImportExportTemplateStatus.DISABLED) {
            return;
        }
        if (templateMapper.disable(template.id()) != 1) {
            throw new IllegalStateException("模板停用失败");
        }
    }

    public ImportExportTemplate findTemplate(Long templateId) {
        return toTemplate(requireTemplate(templateId));
    }

    /** 仅返回已启用的默认模板；未配置时返回空。 */
    public ImportExportTemplate findCurrentDefault(String moduleCode, TemplateType templateType) {
        ImportExportTemplateRecord template = templateMapper.findCurrentDefault(
                requiredCode(moduleCode, "模块编码"), requireTemplateType(templateType)
        );
        return template == null ? null : toTemplate(template);
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

    private void requireAvailableFile(Long fileId) {
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
    }

    private void requireSystemAdmin(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, SYSTEM_ADMIN_ROLE)) {
            throw new IllegalStateException("仅系统管理员可维护导入导出模板");
        }
    }

    private TemplateType requireTemplateType(TemplateType templateType) {
        if (templateType == null) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        return templateType;
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
                Boolean.TRUE.equals(record.defaultTemplate()),
                record.status()
        );
    }
}
