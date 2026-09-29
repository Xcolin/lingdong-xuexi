package com.lingdong.learning.organization.infrastructure.persistence;

/** 机构管理员直接管理的启用组织查询行。 */
public record OrganizationAdminSummaryRow(
        Long id,
        String name,
        String typeCode,
        String path,
        Integer sortOrder
) { }
