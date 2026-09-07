package com.lingdong.learning.templateconfig.application;

/** 通过模板读取权限校验后的下载内容，不暴露存储键或摘要。 */
public record ImportExportTemplateContent(String originalName, String contentType, byte[] content) {
    public ImportExportTemplateContent {
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}
