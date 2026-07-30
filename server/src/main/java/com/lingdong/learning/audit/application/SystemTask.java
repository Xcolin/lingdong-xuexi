package com.lingdong.learning.audit.application;

import java.time.LocalDateTime;

/** Immutable audit task state, separate from learning-process approvals. */
public record SystemTask(Long id, String code, SystemTaskType type, String title, String description,
                         ImpactScope impactScope, SystemTaskStatus status, Long submittedBy,
                         LocalDateTime submittedAt, Long reviewedBy, LocalDateTime reviewedAt,
                         String reviewComment, LocalDateTime createdAt, LocalDateTime updatedAt) { }
