package com.lingdong.learning.templateconfig.application;

import com.lingdong.learning.templateconfig.domain.TemplateType;

import java.util.List;

/** 上传文件并创建一个不可变导入导出模板版本的请求。 */
public record CreateImportExportTemplateUploadCommand(
        Long operatorId,
        String templateName,
        TemplateType templateType,
        String moduleCode,
        String version,
        String originalName,
        String contentType,
        byte[] content,
        boolean defaultTemplate,
        List<ImportTemplateFieldInput> fields
) {
    public CreateImportExportTemplateUploadCommand {
        content = content == null ? null : content.clone();
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}
