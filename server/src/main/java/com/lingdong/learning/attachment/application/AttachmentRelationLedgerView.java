package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileRelationStatus;

import java.time.LocalDateTime;

/** 文件与业务对象关系的审计台账，包含活动和已解除历史。 */
public record AttachmentRelationLedgerView(
        Long id,
        Long fileId,
        String moduleCode,
        Long businessId,
        String relationType,
        String visibleScope,
        FileRelationStatus status,
        LocalDateTime createdAt,
        LocalDateTime releasedAt
) { }
