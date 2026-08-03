package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;

/** 面向应用层的导入导出模板视图。 */
public record ImportExportTemplate(
        Long id,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        String version,
        Long fileId,
        boolean defaultTemplate,
        ImportExportTemplateStatus status
) { }
