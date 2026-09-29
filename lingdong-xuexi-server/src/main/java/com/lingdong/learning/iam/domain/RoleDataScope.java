package com.lingdong.learning.iam.domain;

/**
 * Defines the organizational data boundary granted by a role assignment.
 */
public enum RoleDataScope {
    ALL,
    REGION,
    SCHOOL,
    CLASS,
    SELF,
    CUSTOM
}
