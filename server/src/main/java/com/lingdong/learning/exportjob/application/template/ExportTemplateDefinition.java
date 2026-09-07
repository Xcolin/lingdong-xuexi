package com.lingdong.learning.exportjob.application.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 经安全校验后的有序列定义和原模板列位置。 */
public record ExportTemplateDefinition(
        List<ExportColumnDefinition> columns,
        Map<String, Integer> templateColumnIndexes
) {
    public ExportTemplateDefinition {
        columns = List.copyOf(columns);
        templateColumnIndexes = Map.copyOf(new LinkedHashMap<>(templateColumnIndexes));
    }
}
