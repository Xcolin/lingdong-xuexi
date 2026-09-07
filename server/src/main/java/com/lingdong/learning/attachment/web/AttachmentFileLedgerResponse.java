package com.lingdong.learning.attachment.web;

import com.lingdong.learning.attachment.application.AttachmentFileLedgerView;
import com.lingdong.learning.attachment.domain.FileStatus;

import java.time.LocalDateTime;

/** 附件文件安全台账响应。 */
public record AttachmentFileLedgerResponse(
        String id,
        String originalName,
        String extension,
        String contentType,
        Long sizeBytes,
        String uploaderId,
        String uploaderName,
        String moduleCode,
        String fileCategory,
        Boolean contentSha256Present,
        FileStatus status,
        LocalDateTime uploadedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AttachmentFileLedgerResponse from(AttachmentFileLedgerView view) {
        return new AttachmentFileLedgerResponse(
                view.id().toString(), view.originalName(), view.extension(), view.contentType(), view.sizeBytes(),
                view.uploaderId().toString(), view.uploaderName(), view.moduleCode(), view.fileCategory(),
                view.contentSha256Present(), view.status(), view.uploadedAt(), view.createdAt(), view.updatedAt());
    }
}
