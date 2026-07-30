package com.lingdong.learning.iam.domain;

import java.time.LocalDateTime;

/**
 * Immutable role state shared by the RBAC application and persistence layers.
 */
public record Role(
        Long id,
        String code,
        String name,
        RoleType type,
        RoleDataScope dataScope,
        boolean builtIn,
        RoleStatus status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static Role custom(String code, String name, String description, RoleDataScope dataScope) {
        return new Role(
                null,
                code,
                name,
                RoleType.CUSTOM,
                dataScope,
                false,
                RoleStatus.ENABLED,
                description,
                null,
                null
        );
    }
}
