package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 提供不暴露存储机密的附件文件与业务关系后台台账。 */
@Service
public class AttachmentLedgerApplicationService {
    private static final String FILE_LEDGER_READ_PERMISSION = "ATTACHMENT_FILE_LEDGER_READ";

    private final ManagedFileMapper fileMapper;
    private final FileRelationMapper relationMapper;
    private final PermissionDecisionService permissionDecisionService;

    public AttachmentLedgerApplicationService(
            ManagedFileMapper fileMapper,
            FileRelationMapper relationMapper,
            PermissionDecisionService permissionDecisionService
    ) {
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.permissionDecisionService = permissionDecisionService;
    }

    /** 按组合条件查询最多 200 条安全文件元数据。 */
    public List<AttachmentFileLedgerView> listFiles(AttachmentFileQuery query) {
        Objects.requireNonNull(query, "附件文件查询条件不能为空");
        requirePermission(query.operatorId());
        validateTimeRange(query.createdFrom(), query.createdTo());
        if (query.uploaderId() != null && query.uploaderId() <= 0) {
            throw new IllegalArgumentException("上传人标识必须为正整数");
        }
        return fileMapper.findLedger(
                optionalText(query.originalName(), 255),
                optionalCode(query.moduleCode()),
                optionalCode(query.fileCategory()),
                query.status(),
                query.uploaderId(),
                query.createdFrom(),
                query.createdTo()
        );
    }

    /** 查询指定文件的全部业务关系历史，包括活动和已解除记录。 */
    public List<AttachmentRelationLedgerView> listRelations(Long operatorId, Long fileId) {
        requirePermission(operatorId);
        if (fileId == null || fileMapper.findById(fileId) == null) {
            throw new IllegalArgumentException("附件不存在：" + fileId);
        }
        return relationMapper.findLedgerByFileId(fileId);
    }

    private void requirePermission(Long operatorId) {
        if (!permissionDecisionService.isAllowed(
                operatorId, PermissionClient.WEB, FILE_LEDGER_READ_PERMISSION)) {
            throw new SystemOperationAccessDeniedException("当前账号缺少附件文件台账权限");
        }
    }

    private void validateTimeRange(LocalDateTime createdFrom, LocalDateTime createdTo) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("创建开始时间不能晚于结束时间");
        }
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
}
