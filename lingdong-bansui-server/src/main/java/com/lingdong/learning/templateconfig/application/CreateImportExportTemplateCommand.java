package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.TemplateType;

/** 创建导入或导出模板元数据的请求。 */
public record CreateImportExportTemplateCommand(
        Long operatorId,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        String version,
        Long fileId,
        boolean defaultTemplate
) { }
