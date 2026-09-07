package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;

/** 提供给应用层和接口层的导入模板字段映射视图。 */
public record ImportTemplateFieldView(
        Long id,
        Long templateId,
        String fieldCode,
        String columnName,
        ImportTemplateFieldDataType dataType,
        boolean required,
        Integer maxLength,
        String dictionaryTypeCode,
        int sortOrder
) { }
