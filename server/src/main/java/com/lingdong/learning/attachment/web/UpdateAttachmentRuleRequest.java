package com.lingdong.learning.attachment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 编辑附件规则可变配置请求。 */
public record UpdateAttachmentRuleRequest(
        @NotBlank @Size(max = 100) String ruleName,
        @NotEmpty @Size(max = 50)
        List<@NotBlank @Size(max = 20) @Pattern(regexp = "^\\.?[A-Za-z0-9]+$") String> allowedExtensions,
        @Positive long maxFileSizeBytes,
        @Positive int maxBatchCount,
        boolean previewEnabled,
        @NotNull @PositiveOrZero Long versionNo
) { }
