package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.AttachmentRuleRecord;
import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;
import com.lingdong.learning.attachment.infrastructure.persistence.AttachmentRuleExtensionMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.AttachmentRuleMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 统一处理附件规则配置，以及存储操作开始前的规则校验。 */
@Service
public class AttachmentRuleApplicationService {
    private static final String RULE_READ_PERMISSION = "ATTACHMENT_RULE_READ";
    private static final String RULE_MANAGE_PERMISSION = "ATTACHMENT_RULE_MANAGE";
    private static final String BUSINESS_AUTHORIZED_DOWNLOAD_SCOPE = "BUSINESS_AUTHORIZED";

    private final AttachmentRuleMapper ruleMapper;
    private final AttachmentRuleExtensionMapper extensionMapper;
    private final PermissionDecisionService permissionDecisionService;
    private final IdGenerator idGenerator;

    public AttachmentRuleApplicationService(
            AttachmentRuleMapper ruleMapper,
            AttachmentRuleExtensionMapper extensionMapper,
            PermissionDecisionService permissionDecisionService,
            IdGenerator idGenerator
    ) {
        this.ruleMapper = ruleMapper;
        this.extensionMapper = extensionMapper;
        this.permissionDecisionService = permissionDecisionService;
        this.idGenerator = idGenerator;
    }

    /** 在同一事务内创建启用状态的规则及规范化扩展名白名单。 */
    @Transactional
    public AttachmentRule createRule(CreateAttachmentRuleCommand command) {
        Objects.requireNonNull(command, "附件规则创建请求不能为空");
        requirePermission(command.operatorId(), RULE_MANAGE_PERMISSION);
        String moduleCode = requiredCode(command.moduleCode(), "模块编码");
        String fileCategory = requiredCode(command.fileCategory(), "文件分类");
        String ruleName = requiredText(command.ruleName(), "规则名称", 100);
        List<String> extensions = normalizeExtensions(command.allowedExtensions());
        validateLimits(command.maxFileSizeBytes(), command.maxBatchCount());
        if (ruleMapper.findByModuleAndCategory(moduleCode, fileCategory) != null) {
            throw new IllegalStateException("附件规则已存在：" + moduleCode + "/" + fileCategory);
        }

        AttachmentRuleRecord record = new AttachmentRuleRecord(
                idGenerator.nextId(), moduleCode, fileCategory, ruleName, command.maxFileSizeBytes(),
                command.maxBatchCount(), command.previewEnabled(), BUSINESS_AUTHORIZED_DOWNLOAD_SCOPE,
                AttachmentRuleStatus.ENABLED, 0L, null, null
        );
        if (ruleMapper.insert(record) != 1) {
            throw new IllegalStateException("附件规则保存失败");
        }
        for (String extension : extensions) {
            if (extensionMapper.insert(idGenerator.nextId(), record.id(), extension) != 1) {
                throw new IllegalStateException("附件规则扩展名保存失败");
            }
        }
        return toApplicationRule(record, extensions);
    }

    /** 查询后台规则台账，所有条件均可选且最多返回 200 条。 */
    public List<AttachmentRule> listRules(AttachmentRuleQuery query) {
        Objects.requireNonNull(query, "附件规则查询条件不能为空");
        requirePermission(query.operatorId(), RULE_READ_PERMISSION);
        String ruleName = optionalText(query.ruleName(), 100);
        String moduleCode = optionalCode(query.moduleCode(), "模块编码");
        String fileCategory = optionalCode(query.fileCategory(), "文件分类");
        return ruleMapper.findAll(ruleName, moduleCode, fileCategory, query.status()).stream()
                .map(record -> toApplicationRule(record, extensionMapper.findExtensionsByRuleId(record.id())))
                .toList();
    }

    /** 在保留模块编码和文件分类的前提下更新规则配置。 */
    @Transactional
    public AttachmentRule updateRule(UpdateAttachmentRuleCommand command) {
        Objects.requireNonNull(command, "附件规则编辑请求不能为空");
        requirePermission(command.operatorId(), RULE_MANAGE_PERMISSION);
        AttachmentRuleRecord current = requireRule(command.ruleId());
        Long expectedVersion = requiredVersion(command.versionNo());
        String ruleName = requiredText(command.ruleName(), "规则名称", 100);
        List<String> extensions = normalizeExtensions(command.allowedExtensions());
        validateLimits(command.maxFileSizeBytes(), command.maxBatchCount());
        AttachmentRuleRecord updated = new AttachmentRuleRecord(
                current.id(), current.moduleCode(), current.fileCategory(), ruleName,
                command.maxFileSizeBytes(), command.maxBatchCount(), command.previewEnabled(),
                current.downloadScope(), current.status(), current.versionNo(), current.createdAt(), current.updatedAt()
        );
        if (ruleMapper.updateConfiguration(updated, expectedVersion) != 1) {
            throw versionConflict(current.id());
        }
        extensionMapper.deleteByRuleId(current.id());
        for (String extension : extensions) {
            if (extensionMapper.insert(idGenerator.nextId(), current.id(), extension) != 1) {
                throw new IllegalStateException("附件规则扩展名保存失败");
            }
        }
        return loadApplicationRule(current.id());
    }

    /** 停用规则后，该规则不能再授权新文件登记。 */
    @Transactional
    public AttachmentRule disableRule(Long operatorId, Long ruleId, Long versionNo) {
        return changeStatus(operatorId, ruleId, versionNo, AttachmentRuleStatus.DISABLED);
    }

    /** 恢复规则，使其可以继续授权新文件登记。 */
    @Transactional
    public AttachmentRule enableRule(Long operatorId, Long ruleId, Long versionNo) {
        return changeStatus(operatorId, ruleId, versionNo, AttachmentRuleStatus.ENABLED);
    }

    /** 在对象存储适配器授权上传前校验整批文件。 */
    public void validateNewFiles(String moduleCode, String fileCategory, List<AttachmentCandidate> candidates) {
        AttachmentRule rule = findRule(moduleCode, fileCategory);
        if (rule.status() != AttachmentRuleStatus.ENABLED) {
            throw new IllegalStateException("附件规则已停用：" + rule.moduleCode() + "/" + rule.fileCategory());
        }
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (candidates.size() > rule.maxBatchCount()) {
            throw new IllegalArgumentException("超过规则允许的批量上传数量");
        }
        for (AttachmentCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("上传文件不能为空");
            }
            String extension = extensionOf(requiredText(candidate.originalName(), "文件名称", 255));
            if (!rule.allowedExtensions().contains(extension)) {
                throw new IllegalArgumentException("文件格式不被允许：" + extension);
            }
            if (candidate.sizeBytes() < 0 || candidate.sizeBytes() > rule.maxFileSizeBytes()) {
                throw new IllegalArgumentException("文件大小超出规则限制");
            }
        }
    }

    public AttachmentRule findRule(String moduleCode, String fileCategory) {
        String normalizedModuleCode = requiredCode(moduleCode, "模块编码");
        String normalizedFileCategory = requiredCode(fileCategory, "文件分类");
        AttachmentRuleRecord record = ruleMapper.findByModuleAndCategory(normalizedModuleCode, normalizedFileCategory);
        if (record == null) {
            throw new IllegalArgumentException("未配置附件规则：" + normalizedModuleCode + "/" + normalizedFileCategory);
        }
        return toApplicationRule(record, extensionMapper.findExtensionsByRuleId(record.id()));
    }

    private AttachmentRuleRecord requireRule(Long ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("附件规则标识不能为空");
        }
        AttachmentRuleRecord record = ruleMapper.findById(ruleId);
        if (record == null) {
            throw new IllegalArgumentException("附件规则不存在：" + ruleId);
        }
        return record;
    }

    private AttachmentRule changeStatus(
            Long operatorId,
            Long ruleId,
            Long versionNo,
            AttachmentRuleStatus targetStatus
    ) {
        requirePermission(operatorId, RULE_MANAGE_PERMISSION);
        Long expectedVersion = requiredVersion(versionNo);
        AttachmentRuleRecord current = requireRule(ruleId);
        if (current.status() == targetStatus) {
            return toApplicationRule(current, extensionMapper.findExtensionsByRuleId(current.id()));
        }
        if (ruleMapper.updateStatus(current.id(), targetStatus, current.status(), expectedVersion) != 1) {
            AttachmentRuleRecord latest = requireRule(ruleId);
            if (latest.status() == targetStatus) {
                return toApplicationRule(latest, extensionMapper.findExtensionsByRuleId(latest.id()));
            }
            throw versionConflict(current.id());
        }
        return loadApplicationRule(current.id());
    }

    private AttachmentRule loadApplicationRule(Long ruleId) {
        AttachmentRuleRecord record = requireRule(ruleId);
        return toApplicationRule(record, extensionMapper.findExtensionsByRuleId(ruleId));
    }

    private AttachmentRule toApplicationRule(AttachmentRuleRecord record, List<String> extensions) {
        return new AttachmentRule(record.id(), record.moduleCode(), record.fileCategory(), record.ruleName(),
                List.copyOf(extensions), record.maxFileSizeBytes(), record.maxBatchCount(), record.previewEnabled(),
                record.status(), record.versionNo());
    }

    private List<String> normalizeExtensions(List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            throw new IllegalArgumentException("允许的文件格式不能为空");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String extension : extensions) {
            String value = requiredText(extension, "文件格式", 20).replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
            if (value.isEmpty() || !value.matches("[a-z0-9]+")) {
                throw new IllegalArgumentException("文件格式只能包含字母和数字");
            }
            if (!normalized.add(value)) {
                throw new IllegalArgumentException("文件格式重复：" + value);
            }
        }
        return List.copyOf(normalized);
    }

    private String extensionOf(String filename) {
        int separatorIndex = filename.lastIndexOf('.');
        if (separatorIndex <= 0 || separatorIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件名称缺少扩展名");
        }
        return filename.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void requirePermission(Long userId, String permissionCode) {
        if (!permissionDecisionService.isAllowed(userId, PermissionClient.WEB, permissionCode)) {
            throw new SystemOperationAccessDeniedException("当前账号缺少附件规则管理权限：" + permissionCode);
        }
    }

    private void validateLimits(long maxFileSizeBytes, int maxBatchCount) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("单文件大小限制必须大于零");
        }
        if (maxBatchCount <= 0) {
            throw new IllegalArgumentException("单批上传数量必须大于零");
        }
    }

    private Long requiredVersion(Long versionNo) {
        if (versionNo == null || versionNo < 0) {
            throw new IllegalArgumentException("附件规则版本号必须为非负整数");
        }
        return versionNo;
    }

    private IllegalStateException versionConflict(Long ruleId) {
        return new IllegalStateException("附件规则版本已变化，请刷新后重试：" + ruleId);
    }

    private String optionalCode(String value, String fieldName) {
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
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
