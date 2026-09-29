package com.lingdong.learning.importjob.application;

/** 上传 XLSX 并创建导入校验作业的请求。 */
public record CreateImportJobCommand(
        Long operatorId,
        Long templateId,
        Long organizationId,
        String originalName,
        String contentType,
        byte[] content
) {
    public CreateImportJobCommand {
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}
