package com.lingdong.learning.organization.application;

/** 组织节点低风险编辑命令，不允许修改编码、类型或父节点。 */
public record UpdateOrganizationCommand(
        Long organizationId,
        String name,
        Integer sortOrder,
        Integer versionNo
) {
}
