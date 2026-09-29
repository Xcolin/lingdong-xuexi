package com.lingdong.learning.organization.application;

/**
 * Input for adding a configurable organization type.
 */
public record CreateOrganizationTypeCommand(String code, String name, Integer sortOrder) {
}
