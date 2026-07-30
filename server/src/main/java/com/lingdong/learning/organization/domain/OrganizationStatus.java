package com.lingdong.learning.organization.domain;

/**
 * Disabled organization objects remain available for historical queries but cannot receive new work.
 */
public enum OrganizationStatus {
    ENABLED,
    DISABLED
}
