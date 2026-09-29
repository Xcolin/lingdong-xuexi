package com.lingdong.learning.attachment.application;

/** 学习任务打卡图片上传命令。 */
public record UploadTaskAttachmentCommand(
        String moduleCode,
        String fileCategory,
        String originalName,
        String contentType,
        byte[] content
) {
}
