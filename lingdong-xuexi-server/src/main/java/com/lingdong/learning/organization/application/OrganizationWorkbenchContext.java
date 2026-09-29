package com.lingdong.learning.organization.application;

import java.util.List;

/** 机构管理员小程序工作台所需的最小当前身份上下文。 */
public record OrganizationWorkbenchContext(
        Long userId,
        String username,
        String displayName,
        List<String> permissionCodes,
        List<OrganizationWorkbenchOrganization> organizations
) { }
