package com.lingdong.learning.organization.application;

import com.lingdong.learning.organization.domain.OrganizationChangeType;

/** 系统管理员提交的高风险组织变更申请。 */
public record CreateOrganizationChangeCommand(
        Long submitterId,
        Long organizationId,
        OrganizationChangeType changeType,
        Long targetParentId,
        Integer expectedVersion,
        String reason
) {
}
