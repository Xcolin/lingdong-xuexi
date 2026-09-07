package com.lingdong.learning.templateconfig.web;

import com.lingdong.learning.templateconfig.application.ImportExportTemplate;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;

import java.time.LocalDateTime;

/** 导入导出模板安全响应，雪花标识按字符串输出。 */
public record ImportExportTemplateResponse(
        String id,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        String version,
        String fileId,
        String fileName,
        String contentType,
        Long sizeBytes,
        boolean defaultTemplate,
        ImportExportTemplateStatus status,
        Long versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ImportExportTemplateResponse from(ImportExportTemplate template) {
        return new ImportExportTemplateResponse(
                template.id().toString(), template.templateName(), template.templateType(),
                template.moduleCode(), template.version(), template.fileId().toString(),
                template.fileName(), template.contentType(), template.sizeBytes(),
                template.defaultTemplate(), template.status(), template.versionNo(),
                template.createdAt(), template.updatedAt()
        );
    }
}
