package com.lingdong.learning.templateconfig.domain;

import java.time.LocalDateTime;

/** 导入导出模板在数据库中的完整记录。 */
public record ImportExportTemplateRecord(
        Long id,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        String version,
        Long fileId,
        Boolean defaultTemplate,
        String defaultScopeKey,
        ImportExportTemplateStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
