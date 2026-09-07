package com.lingdong.learning.importjob.application;

import com.lingdong.learning.importjob.domain.ImportJobStatus;

import java.time.LocalDateTime;

/** 导入校验作业台账的组合查询条件。 */
public record ImportJobQuery(
        Long operatorId,
        String jobCode,
        Long templateId,
        Long organizationId,
        ImportJobStatus status,
        LocalDateTime queuedFrom,
        LocalDateTime queuedTo,
        int page,
        int pageSize
) { }
