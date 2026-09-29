package com.lingdong.learning.importjob.domain;

import java.time.LocalDateTime;

/** 不保存原始业务值的导入作业逐行结果。 */
public record ImportJobRowResultRecord(
        Long id,
        Long jobId,
        Integer rowNumber,
        ImportJobRowStatus status,
        String errorSummary,
        LocalDateTime createdAt
) { }
