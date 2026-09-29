package com.lingdong.learning.exportjob.web;

import jakarta.validation.constraints.Size;

/** 敏感导出审核意见。 */
public record ExportJobReviewRequest(@Size(max = 500) String comment) { }
