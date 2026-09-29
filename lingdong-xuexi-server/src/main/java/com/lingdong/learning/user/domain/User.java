package com.lingdong.learning.user.domain;

import java.time.LocalDateTime;

/**
 * Immutable user account state. Authentication secrets are stored only as a hash.
 */
public record User(
        Long id,
        String username,
        String displayName,
        String mobile,
        String passwordHash,
        UserType type,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static User create(Long id, String username, String displayName, String mobile, UserType type) {
        return new User(
                id,
                username,
                displayName,
                mobile,
                null,
                type,
                UserStatus.ENABLED,
                null,
                null
        );
    }
}
