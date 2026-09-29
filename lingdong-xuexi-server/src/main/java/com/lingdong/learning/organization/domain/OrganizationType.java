package com.lingdong.learning.organization.domain;

import java.time.LocalDateTime;

/**
 * Configurable classification used by nodes in the regional and school organization tree.
 */
public record OrganizationType(
        Long id,
        String code,
        String name,
        boolean builtIn,
        OrganizationStatus status,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrganizationType custom(Long id, String code, String name, Integer sortOrder) {
        return new OrganizationType(
                id,
                code,
                name,
                false,
                OrganizationStatus.ENABLED,
                sortOrder,
                null,
                null
        );
    }
}
