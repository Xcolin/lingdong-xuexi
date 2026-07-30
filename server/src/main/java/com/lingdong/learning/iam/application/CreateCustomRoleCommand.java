package com.lingdong.learning.iam.application;

import com.lingdong.learning.iam.domain.RoleDataScope;

/**
 * Input accepted when a system administrator creates a custom RBAC role.
 */
public record CreateCustomRoleCommand(
        String code,
        String name,
        String description,
        RoleDataScope dataScope
) {
}
