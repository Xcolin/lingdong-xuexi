package com.lingdong.learning.attachment.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 启用或停用附件规则时携带的乐观锁版本。 */
public record AttachmentRuleVersionRequest(
        @NotNull @PositiveOrZero Long versionNo
) { }
