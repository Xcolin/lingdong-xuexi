package com.lingdong.learning.templateconfig.web;

import com.lingdong.learning.templateconfig.application.ImportTemplateFieldView;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;

/** 序列化 19 位标识为字符串的导入模板字段响应。 */
public record ImportTemplateFieldResponse(
        String id,
        String templateId,
        String fieldCode,
        String columnName,
        ImportTemplateFieldDataType dataType,
        boolean required,
        Integer maxLength,
        String dictionaryTypeCode,
        int sortOrder
) {
    public static ImportTemplateFieldResponse from(ImportTemplateFieldView field) {
        return new ImportTemplateFieldResponse(
                field.id().toString(), field.templateId().toString(), field.fieldCode(),
                field.columnName(), field.dataType(), field.required(), field.maxLength(),
                field.dictionaryTypeCode(), field.sortOrder());
    }
}
