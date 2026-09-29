package com.lingdong.learning.attachment.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新增附件规则请求。 */
public record CreateAttachmentRuleRequest(
        @NotBlank @Size(max = 64) String moduleCode,
        @NotBlank @Size(max = 64) String fileCategory,
        @NotBlank @Size(max = 100) String ruleName,
        @NotEmpty @Size(max = 50)
        List<@NotBlank @Size(max = 20) @Pattern(regexp = "^\\.?[A-Za-z0-9]+$") String> allowedExtensions,
        @Positive long maxFileSizeBytes,
        @Positive int maxBatchCount,
        boolean previewEnabled
) { }
