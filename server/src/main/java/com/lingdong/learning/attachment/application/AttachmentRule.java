package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;

import java.util.List;

/** Effective rule used by every business module before it registers an attachment. */
public record AttachmentRule(
        Long id, String moduleCode, String fileCategory, String ruleName, List<String> allowedExtensions,
        long maxFileSizeBytes, int maxBatchCount, boolean previewEnabled, AttachmentRuleStatus status
) { }
