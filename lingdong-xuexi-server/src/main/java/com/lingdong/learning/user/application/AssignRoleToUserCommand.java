package com.lingdong.learning.user.application;

/**
 * Input for granting a global role or a role scoped to one associated organization.
 */
public record AssignRoleToUserCommand(Long userId, Long roleId, Long organizationId, Long operatorId) {
    public AssignRoleToUserCommand(Long userId, Long roleId, Long organizationId) {
        this(userId, roleId, organizationId, null);
    }
}
