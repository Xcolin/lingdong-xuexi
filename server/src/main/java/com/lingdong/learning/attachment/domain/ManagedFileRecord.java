package com.lingdong.learning.attachment.domain;

import java.time.LocalDateTime;

/** 统一附件元数据，文件内容由独立存储适配器管理。 */
public record ManagedFileRecord(Long id, String storageKey, String originalName, String extension, String contentType,
                                Long sizeBytes, Long uploaderId, String moduleCode, String fileCategory,
                                String contentSha256, FileStatus status, LocalDateTime uploadedAt,
                                LocalDateTime createdAt, LocalDateTime updatedAt) { }
