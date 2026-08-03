package com.lingdong.learning.attachment.application;

import java.util.List;

/** System-administrator request to configure one module and file-category rule. */
public record CreateAttachmentRuleCommand(
        Long operatorId, String moduleCode, String fileCategory, String ruleName, List<String> allowedExtensions,
        long maxFileSizeBytes, int maxBatchCount, boolean previewEnabled
) { }
