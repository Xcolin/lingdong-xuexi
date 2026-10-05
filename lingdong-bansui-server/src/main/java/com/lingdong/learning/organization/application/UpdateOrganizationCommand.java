package com.lingdong.learning.organization.application;

/** 组织节点低风险编辑命令，不允许修改编码、类型或父节点。 */
public record UpdateOrganizationCommand(
        Long organizationId,
        String name,
        Integer sortOrder,
        Integer versionNo,
        String adminDivisionCode
) {
    /** 兼容既有调用：传 null 表示保持既有行政区划不变。 */
    public UpdateOrganizationCommand(
            Long organizationId, String name, Integer sortOrder, Integer versionNo
    ) {
        this(organizationId, name, sortOrder, versionNo, null);
    }
}
