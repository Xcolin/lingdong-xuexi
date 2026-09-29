package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;

/** 创建或整体替换导入模板字段映射的单项输入。 */
public record ImportTemplateFieldInput(
        String fieldCode,
        String columnName,
        ImportTemplateFieldDataType dataType,
        boolean required,
        Integer maxLength,
        String dictionaryTypeCode,
        int sortOrder
) { }
