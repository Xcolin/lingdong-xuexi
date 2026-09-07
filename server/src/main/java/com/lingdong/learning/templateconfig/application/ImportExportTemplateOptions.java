package com.lingdong.learning.templateconfig.application;

import java.util.List;

/** 汇总模板类型、适用模块和状态三类配置选项。 */
public record ImportExportTemplateOptions(
        List<ImportExportTemplateOption> templateTypes,
        List<ImportExportTemplateOption> modules,
        List<ImportExportTemplateOption> statuses
) {
    public ImportExportTemplateOptions {
        templateTypes = List.copyOf(templateTypes);
        modules = List.copyOf(modules);
        statuses = List.copyOf(statuses);
    }
}
