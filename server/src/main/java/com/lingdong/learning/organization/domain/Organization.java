package com.lingdong.learning.organization.domain;

import java.time.LocalDateTime;

/**
 * An organization node with a materialized code path for later subtree authorization queries.
 */
public record Organization(
        Long id,
        Long parentId,
        String parentScopeKey,
        String code,
        String name,
        String typeCode,
        String path,
        Integer sortOrder,
        OrganizationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static Organization create(
            Long parentId,
            String parentScopeKey,
            String code,
            String name,
            String typeCode,
            String path,
            Integer sortOrder
    ) {
        return new Organization(
                null,
                parentId,
                parentScopeKey,
                code,
                name,
                typeCode,
                path,
                sortOrder,
                OrganizationStatus.ENABLED,
                null,
                null
        );
    }
}
