package com.lingdong.learning.attachment.application;

/** Storage-adapter confirmation of the metadata that was authorized at registration time. */
public record CompleteAttachmentUploadCommand(Long fileId, long sizeBytes, String contentType) { }
