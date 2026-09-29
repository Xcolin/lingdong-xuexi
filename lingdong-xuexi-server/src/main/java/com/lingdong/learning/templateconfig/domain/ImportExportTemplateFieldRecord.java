package com.lingdong.learning.templateconfig.domain;

import java.time.LocalDateTime;

/** 导入模板字段映射的完整持久化记录。 */
public record ImportExportTemplateFieldRecord(
        Long id,
        Long templateId,
        String fieldCode,
        String columnName,
        ImportTemplateFieldDataType dataType,
        Boolean required,
        Integer maxLength,
        String dictionaryTypeCode,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
