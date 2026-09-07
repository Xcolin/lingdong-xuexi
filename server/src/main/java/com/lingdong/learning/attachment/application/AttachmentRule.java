package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;

import java.util.List;

/** 各业务模块在登记附件前统一使用的有效规则。 */
public record AttachmentRule(
        Long id, String moduleCode, String fileCategory, String ruleName, List<String> allowedExtensions,
        long maxFileSizeBytes, int maxBatchCount, boolean previewEnabled, AttachmentRuleStatus status,
        long versionNo
) { }
