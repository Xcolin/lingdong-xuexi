package com.lingdong.learning.importjob.application;

import com.lingdong.learning.importjob.domain.ImportJobRowStatus;

import java.time.LocalDateTime;

/** 不含原始单元格值的逐行校验结果视图。 */
public record ImportJobRowErrorView(
        Long id,
        int rowNumber,
        ImportJobRowStatus status,
        String errorSummary,
        LocalDateTime createdAt
) { }
