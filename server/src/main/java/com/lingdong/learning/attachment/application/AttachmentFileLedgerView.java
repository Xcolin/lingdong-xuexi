package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;

import java.time.LocalDateTime;

/** 文件台账安全投影，不包含存储键、实际路径或内容摘要原文。 */
public record AttachmentFileLedgerView(
        Long id,
        String originalName,
        String extension,
        String contentType,
        Long sizeBytes,
        Long uploaderId,
        String uploaderName,
        String moduleCode,
        String fileCategory,
        Boolean contentSha256Present,
        FileStatus status,
        LocalDateTime uploadedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
