package com.lingdong.learning.attachment.domain;

import java.time.LocalDateTime;

/** Persisted file metadata. File bytes remain in the future object-storage provider. */
public record ManagedFileRecord(Long id, String storageKey, String originalName, String extension, String contentType,
                                Long sizeBytes, Long uploaderId, FileStatus status, LocalDateTime uploadedAt,
                                LocalDateTime createdAt, LocalDateTime updatedAt) { }
