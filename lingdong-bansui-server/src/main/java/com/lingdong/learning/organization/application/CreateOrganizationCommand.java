package com.lingdong.learning.organization.application;

/**
 * Input for adding one regional, school, campus, grade, class, or configured custom organization node.
 */
public record CreateOrganizationCommand(
        String code,
        String name,
        String typeCode,
        Long parentId,
        Integer sortOrder,
        String adminDivisionCode
) {
    /** 兼容既有调用：未指定行政区划时保存为空。 */
    public CreateOrganizationCommand(
            String code, String name, String typeCode, Long parentId, Integer sortOrder
    ) {
        this(code, name, typeCode, parentId, sortOrder, null);
    }
}
