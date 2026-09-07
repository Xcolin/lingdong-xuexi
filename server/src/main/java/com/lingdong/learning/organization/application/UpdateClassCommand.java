package com.lingdong.learning.organization.application;

/** 机构管理员编辑班级名称和排序的应用命令。 */
public record UpdateClassCommand(
        Long classOrganizationId,
        String name,
        Integer sortOrder,
        Integer versionNo
) {
}
