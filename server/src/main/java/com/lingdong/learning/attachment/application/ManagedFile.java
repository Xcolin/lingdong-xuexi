package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;

/** Application projection of registered file metadata. */
public record ManagedFile(Long id, String storageKey, String originalName, String extension, String contentType,
                          long sizeBytes, Long uploaderId, FileStatus status) { }
