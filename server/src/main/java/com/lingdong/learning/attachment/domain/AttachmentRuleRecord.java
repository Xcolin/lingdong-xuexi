package com.lingdong.learning.attachment.domain;

import java.time.LocalDateTime;

/** 附件规则持久化字段，扩展名白名单使用独立表规范化存储。 */
public record AttachmentRuleRecord(
        Long id, String moduleCode, String fileCategory, String ruleName, Long maxFileSizeBytes,
        Integer maxBatchCount, Boolean previewEnabled, String downloadScope, AttachmentRuleStatus status,
        Long versionNo, LocalDateTime createdAt, LocalDateTime updatedAt
) { }
