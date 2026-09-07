package com.lingdong.learning.templateconfig.application;

/** 供模板管理页面使用的启用字典选项。 */
public record ImportExportTemplateOption(
        String code,
        String name,
        boolean defaultOption
) { }
