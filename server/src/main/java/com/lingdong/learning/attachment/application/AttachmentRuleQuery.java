package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.AttachmentRuleStatus;

/** 后台附件规则台账的组合查询条件。 */
public record AttachmentRuleQuery(
        Long operatorId,
        String ruleName,
        String moduleCode,
        String fileCategory,
        AttachmentRuleStatus status
) { }
