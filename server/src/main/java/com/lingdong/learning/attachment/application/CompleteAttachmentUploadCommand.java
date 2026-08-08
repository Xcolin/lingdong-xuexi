package com.lingdong.learning.attachment.application;

/** 存储完成后回写已授权元数据与内容摘要。 */
public record CompleteAttachmentUploadCommand(
        Long fileId, long sizeBytes, String contentType, String contentSha256
) {
    public CompleteAttachmentUploadCommand(Long fileId, long sizeBytes, String contentType) {
        this(fileId, sizeBytes, contentType, null);
    }
}
