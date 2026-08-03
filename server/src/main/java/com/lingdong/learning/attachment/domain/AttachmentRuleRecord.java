package com.lingdong.learning.attachment.domain;

import java.time.LocalDateTime;

/** Persisted attachment-rule fields excluding its separately normalized extension allowlist. */
public record AttachmentRuleRecord(
        Long id, String moduleCode, String fileCategory, String ruleName, Long maxFileSizeBytes,
        Integer maxBatchCount, Boolean previewEnabled, String downloadScope, AttachmentRuleStatus status,
        LocalDateTime createdAt, LocalDateTime updatedAt
) { }
