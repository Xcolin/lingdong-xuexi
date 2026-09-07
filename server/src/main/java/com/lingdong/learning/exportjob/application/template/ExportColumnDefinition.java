package com.lingdong.learning.exportjob.application.template;

/** 适配器声明的稳定导出列，不接受客户端字段表达式。 */
public record ExportColumnDefinition(
        String code,
        String header,
        boolean defaultSelected
) { }
