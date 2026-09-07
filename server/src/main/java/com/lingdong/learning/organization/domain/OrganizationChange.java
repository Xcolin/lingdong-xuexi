package com.lingdong.learning.organization.domain;

import java.time.LocalDateTime;

/** 高风险组织变更申请快照，审批期间不随组织现状变化。 */
public record OrganizationChange(
        Long id,
        Long taskId,
        Long organizationId,
        OrganizationChangeType changeType,
        Long targetParentId,
        Integer expectedVersion,
        String organizationCodeSnapshot,
        String organizationNameSnapshot,
        Long fromParentIdSnapshot,
        String reason,
        OrganizationChangeExecutionStatus executionStatus,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OrganizationChange pending(
            Long id,
            Long taskId,
            Organization organization,
            OrganizationChangeType changeType,
            Long targetParentId,
            String reason
    ) {
        return new OrganizationChange(
                id, taskId, organization.id(), changeType, targetParentId,
                organization.versionNo(), organization.code(), organization.name(),
                organization.parentId(), reason, OrganizationChangeExecutionStatus.PENDING,
                null, null, null
        );
    }
}
