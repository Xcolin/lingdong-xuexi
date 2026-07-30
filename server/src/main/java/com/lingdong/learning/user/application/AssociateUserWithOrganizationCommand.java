package com.lingdong.learning.user.application;

/**
 * Input for linking an existing account to one regional or school organization node.
 */
public record AssociateUserWithOrganizationCommand(Long userId, Long organizationId) {
}
