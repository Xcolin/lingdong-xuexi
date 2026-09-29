package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileRelationStatus;

/** Application projection of one file visibility relation. */
public record FileRelation(Long id, Long fileId, String moduleCode, Long businessId, String relationType,
                           String visibleScope, FileRelationStatus status) { }
