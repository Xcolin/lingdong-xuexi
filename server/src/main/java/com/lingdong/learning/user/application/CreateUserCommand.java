package com.lingdong.learning.user.application;

import com.lingdong.learning.user.domain.UserType;

/**
 * Input for creating a managed platform, organization, family, or student account.
 */
public record CreateUserCommand(String username, String displayName, String mobile, UserType type, Long operatorId) {
    public CreateUserCommand(String username, String displayName, String mobile, UserType type) {
        this(username, displayName, mobile, type, null);
    }
}
