package com.lingdong.learning.attachment.application;

/** 面向业务端返回的附件摘要，不暴露内部存储键。 */
public record TaskAttachmentView(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String contentUrl
) {
}
