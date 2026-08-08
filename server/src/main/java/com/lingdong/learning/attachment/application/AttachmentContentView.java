package com.lingdong.learning.attachment.application;

/** 通过对象级鉴权后的附件内容响应。 */
public record AttachmentContentView(String originalName, String contentType, byte[] content) {
}
