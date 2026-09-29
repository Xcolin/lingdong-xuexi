package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;

/** 导入导出模板后台台账的组合查询条件。 */
public record ImportExportTemplateQuery(
        Long operatorId,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        ImportExportTemplateStatus status
) { }
