package com.lingdong.learning.attachment.application;

/** Metadata accepted before a storage adapter receives a real upload request. */
public record RegisterAttachmentFileCommand(Long uploaderId, String moduleCode, String fileCategory,
                                            String originalName, String contentType, long sizeBytes) { }
