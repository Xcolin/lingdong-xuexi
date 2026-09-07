package com.lingdong.learning.attachment.web;

import com.lingdong.learning.attachment.application.AttachmentRelationLedgerView;
import com.lingdong.learning.attachment.domain.FileRelationStatus;

import java.time.LocalDateTime;

/** 附件业务关系历史响应。 */
public record AttachmentRelationLedgerResponse(
        String id,
        String fileId,
        String moduleCode,
        String businessId,
        String relationType,
        String visibleScope,
        FileRelationStatus status,
        LocalDateTime createdAt,
        LocalDateTime releasedAt
) {
    public static AttachmentRelationLedgerResponse from(AttachmentRelationLedgerView view) {
        return new AttachmentRelationLedgerResponse(
                view.id().toString(), view.fileId().toString(), view.moduleCode(), view.businessId().toString(),
                view.relationType(), view.visibleScope(), view.status(), view.createdAt(), view.releasedAt());
    }
}
