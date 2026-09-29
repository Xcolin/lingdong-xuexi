package com.lingdong.learning.exportjob.domain;

import java.time.LocalDateTime;

/** 不可变导出作业状态事件，不保存筛选明文和文件路径。 */
public record ExportJobEventRecord(
        Long id,
        Long jobId,
        ExportJobEventType eventType,
        Long operatorId,
        String summary,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) { }
