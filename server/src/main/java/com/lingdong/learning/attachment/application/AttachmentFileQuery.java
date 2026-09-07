package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;

import java.time.LocalDateTime;

/** 后台文件台账的组合查询条件。 */
public record AttachmentFileQuery(
        Long operatorId,
        String originalName,
        String moduleCode,
        String fileCategory,
        FileStatus status,
        Long uploaderId,
        LocalDateTime createdFrom,
        LocalDateTime createdTo
) { }
