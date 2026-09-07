package com.lingdong.learning.attachment.domain;

import java.time.LocalDateTime;

/** 持久化业务可见关系；解除关系不会删除关联文件元数据。 */
public record FileRelationRecord(Long id, Long fileId, String moduleCode, Long businessId, String relationType,
                                 String visibleScope, FileRelationStatus status, LocalDateTime createdAt,
                                 LocalDateTime releasedAt) { }
