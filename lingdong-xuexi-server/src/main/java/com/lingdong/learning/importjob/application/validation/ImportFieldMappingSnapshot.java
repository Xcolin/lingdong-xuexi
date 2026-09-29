package com.lingdong.learning.importjob.application.validation;

import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;

import java.util.Set;

/** 作业创建时固化的单个导入字段及字典值快照。 */
public record ImportFieldMappingSnapshot(
        String fieldCode,
        String columnName,
        ImportTemplateFieldDataType dataType,
        boolean required,
        Integer maxLength,
        Set<String> dictionaryValues,
        int sortOrder
) {
    public ImportFieldMappingSnapshot {
        dictionaryValues = dictionaryValues == null ? Set.of() : Set.copyOf(dictionaryValues);
    }
}
