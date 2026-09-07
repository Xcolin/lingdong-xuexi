package com.lingdong.learning.attachment.application;

import java.util.List;

/** 具备附件规则管理权限的账号创建模块分类规则时提交的参数。 */
public record CreateAttachmentRuleCommand(
        Long operatorId, String moduleCode, String fileCategory, String ruleName, List<String> allowedExtensions,
        long maxFileSizeBytes, int maxBatchCount, boolean previewEnabled
) { }
