package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateFieldRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateFieldMapper;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 管理导入模板的有序字段映射和引用后的不可变边界。 */
@Service
public class ImportTemplateFieldApplicationService {
    private static final String READ_PERMISSION = "IMPORT_EXPORT_TEMPLATE_READ";
    private static final String MANAGE_PERMISSION = "IMPORT_EXPORT_TEMPLATE_MANAGE";
    private static final Pattern FIELD_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,64}");
    private static final Pattern DICTIONARY_CODE_PATTERN = Pattern.compile("[A-Z0-9_]{1,64}");

    private final ImportExportTemplateMapper templateMapper;
    private final ImportExportTemplateFieldMapper fieldMapper;
    private final ImportTemplateUsageQuery usageQuery;
    private final PermissionDecisionService permissionDecisionService;
    private final DictionaryItemMapper dictionaryItemMapper;
    private final IdGenerator idGenerator;

    public ImportTemplateFieldApplicationService(
            ImportExportTemplateMapper templateMapper,
            ImportExportTemplateFieldMapper fieldMapper,
            ImportTemplateUsageQuery usageQuery,
            PermissionDecisionService permissionDecisionService,
            DictionaryItemMapper dictionaryItemMapper,
            IdGenerator idGenerator
    ) {
        this.templateMapper = templateMapper;
        this.fieldMapper = fieldMapper;
        this.usageQuery = usageQuery;
        this.permissionDecisionService = permissionDecisionService;
        this.dictionaryItemMapper = dictionaryItemMapper;
        this.idGenerator = idGenerator;
    }

    /** 查询模板当前字段映射，停用状态不影响有权限用户读取。 */
    @Transactional(readOnly = true)
    public List<ImportTemplateFieldView> findByTemplate(Long operatorId, Long templateId) {
        requirePermission(operatorId, READ_PERMISSION);
        requireTemplate(templateId);
        return toViews(fieldMapper.findByTemplateId(templateId));
    }

    /** 为停用且未被作业引用的导入模板整体替换字段映射。 */
    @Transactional
    public List<ImportTemplateFieldView> replace(ReplaceImportTemplateFieldsCommand command) {
        Objects.requireNonNull(command, "字段映射替换请求不能为空");
        requirePermission(command.operatorId(), MANAGE_PERMISSION);
        ImportExportTemplateRecord template = requireTemplate(command.templateId());
        requireImportTemplate(template);
        if (template.status() != ImportExportTemplateStatus.DISABLED) {
            throw new IllegalStateException("模板必须先停用才能修改字段映射");
        }
        Long expectedVersion = requireVersion(command.versionNo());
        if (!expectedVersion.equals(template.versionNo())) {
            throw versionConflict(template.id());
        }
        if (usageQuery.hasAnyJob(template.id())) {
            throw new IllegalStateException("模板已有导入作业引用，字段映射不可修改");
        }
        List<ImportExportTemplateFieldRecord> fields = normalize(template.id(), command.fields(), true);
        if (templateMapper.incrementVersionIfDisabled(template.id(), expectedVersion) != 1) {
            throw versionConflict(template.id());
        }
        fieldMapper.deleteByTemplateId(template.id());
        insertAll(fields);
        return toViews(fields);
    }

    /** 在新模板事务内写入字段，不重复执行权限和版本检查。 */
    public List<ImportTemplateFieldView> insertForNewTemplate(
            Long templateId,
            TemplateType templateType,
            List<ImportTemplateFieldInput> inputs
    ) {
        if (templateType == TemplateType.EXPORT) {
            if (inputs != null && !inputs.isEmpty()) {
                throw new IllegalArgumentException("导出模板不能配置导入字段映射");
            }
            return List.of();
        }
        if (templateType != TemplateType.IMPORT) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        List<ImportExportTemplateFieldRecord> fields = normalize(templateId, inputs, true);
        insertAll(fields);
        return toViews(fields);
    }

    /** 在保存附件前验证新模板字段，避免无效请求产生临时物理内容。 */
    public void validateForNewTemplate(
            TemplateType templateType,
            List<ImportTemplateFieldInput> inputs
    ) {
        if (templateType == TemplateType.EXPORT) {
            if (inputs != null && !inputs.isEmpty()) {
                throw new IllegalArgumentException("导出模板不能配置导入字段映射");
            }
            return;
        }
        if (templateType != TemplateType.IMPORT) {
            throw new IllegalArgumentException("模板类型不能为空");
        }
        normalize(0L, inputs, false);
    }

    /** 判断模板是否具备创建导入校验作业所需的字段映射。 */
    @Transactional(readOnly = true)
    public boolean hasFields(Long templateId) {
        return !fieldMapper.findByTemplateId(templateId).isEmpty();
    }

    private void insertAll(List<ImportExportTemplateFieldRecord> fields) {
        if (fieldMapper.insertBatch(fields) != fields.size()) {
            throw new IllegalStateException("导入模板字段映射保存失败");
        }
    }

    private List<ImportExportTemplateFieldRecord> normalize(
            Long templateId,
            List<ImportTemplateFieldInput> inputs,
            boolean assignIds
    ) {
        if (templateId == null) {
            throw new IllegalArgumentException("模板标识不能为空");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("导入模板至少配置一个字段");
        }
        Set<String> fieldCodes = new HashSet<>();
        Set<String> columnNames = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        List<ImportExportTemplateFieldRecord> result = new ArrayList<>(inputs.size());
        for (ImportTemplateFieldInput input : inputs) {
            if (input == null) {
                throw new IllegalArgumentException("字段映射项不能为空");
            }
            String fieldCode = requiredCode(input.fieldCode(), "字段编码", FIELD_CODE_PATTERN);
            String columnName = requiredText(input.columnName(), "表头名称", 100);
            ImportTemplateFieldDataType dataType = Objects.requireNonNull(
                    input.dataType(), "字段数据类型不能为空");
            if (input.sortOrder() < 0) {
                throw new IllegalArgumentException("字段排序必须为非负整数");
            }
            if (!fieldCodes.add(fieldCode)) {
                throw new IllegalArgumentException("字段编码不能重复：" + fieldCode);
            }
            if (!columnNames.add(columnName)) {
                throw new IllegalArgumentException("表头名称不能重复：" + columnName);
            }
            if (!sortOrders.add(input.sortOrder())) {
                throw new IllegalArgumentException("字段排序不能重复：" + input.sortOrder());
            }
            Integer maxLength = normalizeMaxLength(dataType, input.maxLength());
            String dictionaryTypeCode = optionalDictionaryCode(input.dictionaryTypeCode());
            if (dataType == ImportTemplateFieldDataType.BOOLEAN && dictionaryTypeCode != null) {
                throw new IllegalArgumentException("布尔字段不能配置数据字典");
            }
            if (dictionaryTypeCode != null
                    && dictionaryItemMapper.findEnabledByTypeCode(dictionaryTypeCode).isEmpty()) {
                throw new IllegalArgumentException("数据字典不存在或没有启用项：" + dictionaryTypeCode);
            }
            result.add(new ImportExportTemplateFieldRecord(
                    assignIds ? idGenerator.nextId() : 0L, templateId, fieldCode, columnName, dataType,
                    input.required(), maxLength, dictionaryTypeCode, input.sortOrder(), null, null
            ));
        }
        result.sort(java.util.Comparator.comparing(ImportExportTemplateFieldRecord::sortOrder));
        return List.copyOf(result);
    }

    private Integer normalizeMaxLength(ImportTemplateFieldDataType dataType, Integer maxLength) {
        if (dataType == ImportTemplateFieldDataType.TEXT) {
            if (maxLength == null || maxLength < 1 || maxLength > 1000) {
                throw new IllegalArgumentException("文本字段最大长度必须为 1 至 1000");
            }
            return maxLength;
        }
        if (maxLength != null) {
            throw new IllegalArgumentException("仅文本字段可以配置最大长度");
        }
        return null;
    }

    private String optionalDictionaryCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return requiredCode(value, "数据字典编码", DICTIONARY_CODE_PATTERN);
    }

    private String requiredCode(String value, String fieldName, Pattern pattern) {
        String normalized = requiredText(value, fieldName, 64).toUpperCase(Locale.ROOT);
        if (!normalized.equals(value.trim()) || !pattern.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + "仅允许大写英文字母、数字和下划线");
        }
        return normalized;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private Long requireVersion(Long versionNo) {
        if (versionNo == null || versionNo < 0) {
            throw new IllegalArgumentException("模板版本号必须为非负整数");
        }
        return versionNo;
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

    private void requireImportTemplate(ImportExportTemplateRecord template) {
        if (template.templateType() != TemplateType.IMPORT) {
            throw new IllegalStateException("仅导入模板可以配置字段映射");
        }
    }

    private void requirePermission(Long userId, String permissionCode) {
        if (!permissionDecisionService.isAllowed(userId, PermissionClient.WEB, permissionCode)) {
            throw new SystemOperationAccessDeniedException(
                    "当前账号缺少导入导出模板管理权限：" + permissionCode);
        }
    }

    private IllegalStateException versionConflict(Long templateId) {
        return new IllegalStateException("模板版本已变化，请刷新后重试：" + templateId);
    }

    private List<ImportTemplateFieldView> toViews(List<ImportExportTemplateFieldRecord> fields) {
        return fields.stream().map(field -> new ImportTemplateFieldView(
                field.id(), field.templateId(), field.fieldCode(), field.columnName(), field.dataType(),
                Boolean.TRUE.equals(field.required()), field.maxLength(), field.dictionaryTypeCode(),
                field.sortOrder()
        )).toList();
    }
}
