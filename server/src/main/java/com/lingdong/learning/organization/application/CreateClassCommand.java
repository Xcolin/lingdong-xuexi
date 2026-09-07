package com.lingdong.learning.organization.application;

/** 机构管理员在授权学校下新增班级的应用命令。 */
public record CreateClassCommand(
        Long schoolOrganizationId,
        String name,
        Integer sortOrder
) {
}
