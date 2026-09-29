package com.lingdong.learning.exportjob.infrastructure.persistence;

import java.time.LocalDateTime;

/** 模板台账只读取公开的配置版本字段。 */
public record TemplateLedgerExportRow(Long id, String templateName, String templateType,
        String moduleCode, String version, String status, LocalDateTime updatedAt) { }
