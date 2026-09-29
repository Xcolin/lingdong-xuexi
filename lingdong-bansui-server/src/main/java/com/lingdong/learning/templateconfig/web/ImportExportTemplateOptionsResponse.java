package com.lingdong.learning.templateconfig.web;

import com.lingdong.learning.templateconfig.application.ImportExportTemplateOption;
import com.lingdong.learning.templateconfig.application.ImportExportTemplateOptions;

import java.util.List;

/** 模板页面所需的当前启用字典选项。 */
public record ImportExportTemplateOptionsResponse(
        List<OptionResponse> templateTypes,
        List<OptionResponse> modules,
        List<OptionResponse> statuses
) {
    public static ImportExportTemplateOptionsResponse from(ImportExportTemplateOptions options) {
        return new ImportExportTemplateOptionsResponse(
                map(options.templateTypes()), map(options.modules()), map(options.statuses())
        );
    }

    private static List<OptionResponse> map(List<ImportExportTemplateOption> options) {
        return options.stream().map(OptionResponse::from).toList();
    }

    /** 单个字典选项，不暴露字典内部标识。 */
    public record OptionResponse(String code, String name, boolean defaultItem) {
        private static OptionResponse from(ImportExportTemplateOption option) {
            return new OptionResponse(option.code(), option.name(), option.defaultOption());
        }
    }
}
