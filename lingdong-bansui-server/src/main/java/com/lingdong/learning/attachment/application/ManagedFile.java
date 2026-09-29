package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;

/** 统一附件应用层元数据。 */
public record ManagedFile(Long id, String storageKey, String originalName, String extension, String contentType,
                          long sizeBytes, Long uploaderId, String moduleCode, String fileCategory,
                          String contentSha256, FileStatus status) { }
