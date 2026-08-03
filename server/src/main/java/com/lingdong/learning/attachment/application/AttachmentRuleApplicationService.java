package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.AttachmentRuleRecord;
import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;
import com.lingdong.learning.attachment.infrastructure.persistence.AttachmentRuleExtensionMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.AttachmentRuleMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Centralizes attachment configuration and validation before any storage operation may begin. */
@Service
public class AttachmentRuleApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";
    private static final String BUSINESS_AUTHORIZED_DOWNLOAD_SCOPE = "BUSINESS_AUTHORIZED";

    private final AttachmentRuleMapper ruleMapper;
    private final AttachmentRuleExtensionMapper extensionMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdGenerator idGenerator;

    public AttachmentRuleApplicationService(
            AttachmentRuleMapper ruleMapper,
            AttachmentRuleExtensionMapper extensionMapper,
            UserRoleMapper userRoleMapper,
            IdGenerator idGenerator
    ) {
        this.ruleMapper = ruleMapper;
        this.extensionMapper = extensionMapper;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
    }

    /** Creates an enabled rule and its normalized extension allowlist atomically. */
    @Transactional
    public AttachmentRule createRule(CreateAttachmentRuleCommand command) {
        Objects.requireNonNull(command, "附件规则创建请求不能为空");
        requireSystemAdmin(command.operatorId());
        String moduleCode = requiredCode(command.moduleCode(), "模块编码");
        String fileCategory = requiredCode(command.fileCategory(), "文件分类");
        String ruleName = requiredText(command.ruleName(), "规则名称", 100);
        List<String> extensions = normalizeExtensions(command.allowedExtensions());
        if (command.maxFileSizeBytes() <= 0) {
            throw new IllegalArgumentException("单文件大小限制必须大于零");
        }
        if (command.maxBatchCount() <= 0) {
            throw new IllegalArgumentException("单批上传数量必须大于零");
        }
        if (ruleMapper.findByModuleAndCategory(moduleCode, fileCategory) != null) {
            throw new IllegalStateException("附件规则已存在：" + moduleCode + "/" + fileCategory);
        }

        AttachmentRuleRecord record = new AttachmentRuleRecord(
                idGenerator.nextId(), moduleCode, fileCategory, ruleName, command.maxFileSizeBytes(),
                command.maxBatchCount(), command.previewEnabled(), BUSINESS_AUTHORIZED_DOWNLOAD_SCOPE,
                AttachmentRuleStatus.ENABLED, null, null
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

    /** Stops a rule immediately so it cannot authorize new file registrations. */
    @Transactional
    public void disableRule(Long operatorId, Long ruleId) {
        requireSystemAdmin(operatorId);
        AttachmentRuleRecord record = requireRule(ruleId);
        if (record.status() == AttachmentRuleStatus.DISABLED) {
            return;
        }
        if (ruleMapper.updateStatus(record.id(), AttachmentRuleStatus.DISABLED) != 1) {
            throw new IllegalStateException("附件规则停用失败");
        }
    }

    /** Validates a batch before the object-storage adapter is asked to authorize an upload. */
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

    private AttachmentRule toApplicationRule(AttachmentRuleRecord record, List<String> extensions) {
        return new AttachmentRule(record.id(), record.moduleCode(), record.fileCategory(), record.ruleName(),
                List.copyOf(extensions), record.maxFileSizeBytes(), record.maxBatchCount(), record.previewEnabled(), record.status());
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

    private void requireSystemAdmin(Long userId) {
        if (userId == null || !userRoleMapper.hasRoleCode(userId, SYSTEM_ADMIN_ROLE)) {
            throw new IllegalStateException("仅系统管理员可维护附件规则");
        }
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
