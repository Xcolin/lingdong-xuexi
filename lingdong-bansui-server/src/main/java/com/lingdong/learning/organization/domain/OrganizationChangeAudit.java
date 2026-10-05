package com.lingdong.learning.organization.domain;

import java.time.LocalDateTime;

/** 组织变更审计不依赖目标组织外键，受控删除后仍可追溯。 */
public record OrganizationChangeAudit(
        Long id,
        Long organizationId,
        Long taskId,
        OrganizationChangeAuditEvent eventType,
        OrganizationChangeType changeType,
        String beforeName,
        String afterName,
        Integer beforeSortOrder,
        Integer afterSortOrder,
        Long beforeParentId,
        Long afterParentId,
        OrganizationStatus beforeStatus,
        OrganizationStatus afterStatus,
        Long operatorUserId,
        String reason,
        LocalDateTime occurredAt
) {
    public static OrganizationChangeAudit create(
            Long id,
            Organization organization,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, organization.id(), null, OrganizationChangeAuditEvent.CREATE, null,
                null, organization.name(), null, organization.sortOrder(),
                null, organization.parentId(), null, organization.status(),
                operatorUserId, null, occurredAt
        );
    }

    public static OrganizationChangeAudit directUpdate(
            Long id,
            Organization before,
            Organization after,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, before.id(), null, OrganizationChangeAuditEvent.DIRECT_UPDATE, null,
                before.name(), after.name(), before.sortOrder(), after.sortOrder(),
                before.parentId(), after.parentId(), before.status(), after.status(),
                operatorUserId, null, occurredAt
        );
    }

    /** 拖拽同级排序直接生效：记录前后顺序。 */
    public static OrganizationChangeAudit directReorder(
            Long id,
            Organization before,
            Integer afterSortOrder,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, before.id(), null, OrganizationChangeAuditEvent.DIRECT_REORDER, null,
                before.name(), before.name(), before.sortOrder(), afterSortOrder,
                before.parentId(), before.parentId(), before.status(), before.status(),
                operatorUserId, null, occurredAt
        );
    }

    /** 拖拽改父级直接生效：记录前后父级与顺序。 */
    public static OrganizationChangeAudit directMove(
            Long id,
            Organization before,
            Organization after,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, before.id(), null, OrganizationChangeAuditEvent.DIRECT_MOVE, null,
                before.name(), after.name(), before.sortOrder(), after.sortOrder(),
                before.parentId(), after.parentId(), before.status(), after.status(),
                operatorUserId, null, occurredAt
        );
    }

    public static OrganizationChangeAudit enable(
            Long id,
            Organization before,
            Organization after,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, before.id(), null, OrganizationChangeAuditEvent.ENABLE, null,
                before.name(), after.name(), before.sortOrder(), after.sortOrder(),
                before.parentId(), after.parentId(), before.status(), after.status(),
                operatorUserId, null, occurredAt
        );
    }

    public static OrganizationChangeAudit disable(
            Long id,
            Organization before,
            Organization after,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, before.id(), null, OrganizationChangeAuditEvent.DISABLE, null,
                before.name(), after.name(), before.sortOrder(), after.sortOrder(),
                before.parentId(), after.parentId(), before.status(), after.status(),
                operatorUserId, null, occurredAt
        );
    }

    public static OrganizationChangeAudit request(
            Long id,
            Organization organization,
            Long taskId,
            OrganizationChangeType changeType,
            Long targetParentId,
            Long operatorUserId,
            String reason,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, organization.id(), taskId, OrganizationChangeAuditEvent.REQUEST, changeType,
                organization.name(), organization.name(), organization.sortOrder(), organization.sortOrder(),
                organization.parentId(), targetParentId, organization.status(), organization.status(),
                operatorUserId, reason, occurredAt
        );
    }

    public static OrganizationChangeAudit review(
            Long id,
            OrganizationChange change,
            OrganizationChangeAuditEvent eventType,
            Long operatorUserId,
            String reviewComment,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, change.organizationId(), change.taskId(), eventType, change.changeType(),
                change.organizationNameSnapshot(), change.organizationNameSnapshot(), null, null,
                change.fromParentIdSnapshot(), change.targetParentId(), null, null,
                operatorUserId, reviewComment, occurredAt
        );
    }

    public static OrganizationChangeAudit apply(
            Long id,
            OrganizationChange change,
            Organization before,
            Organization after,
            Long operatorUserId,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, change.organizationId(), change.taskId(), OrganizationChangeAuditEvent.APPLY,
                change.changeType(), before.name(), after == null ? null : after.name(),
                before.sortOrder(), after == null ? null : after.sortOrder(),
                before.parentId(), after == null ? change.targetParentId() : after.parentId(),
                before.status(), after == null ? null : after.status(), operatorUserId,
                change.reason(), occurredAt
        );
    }

    public static OrganizationChangeAudit executionFailed(
            Long id,
            OrganizationChange change,
            Long operatorUserId,
            String failureReason,
            LocalDateTime occurredAt
    ) {
        return new OrganizationChangeAudit(
                id, change.organizationId(), change.taskId(), OrganizationChangeAuditEvent.EXECUTION_FAILED,
                change.changeType(), change.organizationNameSnapshot(), change.organizationNameSnapshot(),
                null, null, change.fromParentIdSnapshot(), change.targetParentId(), null, null,
                operatorUserId, failureReason, occurredAt
        );
    }
}
