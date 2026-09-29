package com.lingdong.learning.templateconfig.web;

import com.lingdong.learning.templateconfig.application.ImportTemplateFieldInput;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;

/** 接收一个导入模板字段映射。 */
public record ImportTemplateFieldRequest(
        String fieldCode,
        String columnName,
        ImportTemplateFieldDataType dataType,
        boolean required,
        Integer maxLength,
        String dictionaryTypeCode,
        int sortOrder
) {
    public ImportTemplateFieldInput toInput() {
        return new ImportTemplateFieldInput(
                fieldCode, columnName, dataType, required, maxLength, dictionaryTypeCode, sortOrder);
    }
}
