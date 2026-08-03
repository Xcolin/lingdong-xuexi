package com.lingdong.learning.attachment.domain;

import java.time.LocalDateTime;

/** Persisted business visibility relation; releasing it never deletes the linked file metadata. */
public record FileRelationRecord(Long id, Long fileId, String moduleCode, Long businessId, String relationType,
                                 String visibleScope, FileRelationStatus status, LocalDateTime createdAt,
                                 LocalDateTime releasedAt) { }
