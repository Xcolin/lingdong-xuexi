package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;

import java.time.LocalDateTime;

/** 面向应用层的导入导出模板视图。 */
public record ImportExportTemplate(
        Long id,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        String version,
        Long fileId,
        String fileName,
        String contentType,
        Long sizeBytes,
        boolean defaultTemplate,
        ImportExportTemplateStatus status,
        Long versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
