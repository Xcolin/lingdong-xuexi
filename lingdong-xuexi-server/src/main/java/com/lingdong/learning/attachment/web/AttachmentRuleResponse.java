package com.lingdong.learning.attachment.web;

import com.lingdong.learning.attachment.application.AttachmentRule;
import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;

import java.util.List;

/** 附件规则后台响应，雪花标识按字符串输出。 */
public record AttachmentRuleResponse(
        String id,
        String moduleCode,
        String fileCategory,
        String ruleName,
        List<String> allowedExtensions,
        long maxFileSizeBytes,
        int maxBatchCount,
        boolean previewEnabled,
        AttachmentRuleStatus status,
        long versionNo
) {
    public static AttachmentRuleResponse from(AttachmentRule rule) {
        return new AttachmentRuleResponse(
                rule.id().toString(), rule.moduleCode(), rule.fileCategory(), rule.ruleName(),
                rule.allowedExtensions(), rule.maxFileSizeBytes(), rule.maxBatchCount(),
                rule.previewEnabled(), rule.status(), rule.versionNo());
    }
}
