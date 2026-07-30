package com.lingdong.learning.organization.application;

/**
 * Input for adding one regional, school, campus, grade, class, or configured custom organization node.
 */
public record CreateOrganizationCommand(
        String code,
        String name,
        String typeCode,
        Long parentId,
        Integer sortOrder
) {
}
