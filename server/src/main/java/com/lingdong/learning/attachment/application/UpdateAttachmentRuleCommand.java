package com.lingdong.learning.attachment.application;

import java.util.List;

/** 编辑附件规则可变配置时提交的参数，模块编码和文件分类不可变。 */
public record UpdateAttachmentRuleCommand(
        Long operatorId,
        Long ruleId,
        String ruleName,
        List<String> allowedExtensions,
        long maxFileSizeBytes,
        int maxBatchCount,
        boolean previewEnabled,
        Long versionNo
) { }
