package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.organization.application.OrganizationChangeReviewItem;
import com.lingdong.learning.organization.domain.OrganizationChangeExecutionStatus;
import com.lingdong.learning.organization.domain.OrganizationChangeType;

import java.time.LocalDateTime;

/** 组织变更申请快照、执行状态与审核状态的统一响应。 */
public record OrganizationChangeResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long changeId,
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        @JsonSerialize(using = ToStringSerializer.class) Long organizationId,
        OrganizationChangeType changeType,
        @JsonSerialize(using = ToStringSerializer.class) Long targetParentId,
        Integer expectedVersion,
        String organizationCodeSnapshot,
        String organizationNameSnapshot,
        @JsonSerialize(using = ToStringSerializer.class) Long fromParentIdSnapshot,
        String reason,
        OrganizationChangeExecutionStatus executionStatus,
        String failureReason,
        SystemTaskStatus taskStatus,
        @JsonSerialize(using = ToStringSerializer.class) Long submittedBy,
        LocalDateTime submittedAt,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment,
        LocalDateTime createdAt
) {
    static OrganizationChangeResponse from(OrganizationChangeReviewItem item) {
        return new OrganizationChangeResponse(
                item.change().id(), item.change().taskId(), item.change().organizationId(),
                item.change().changeType(), item.change().targetParentId(), item.change().expectedVersion(),
                item.change().organizationCodeSnapshot(), item.change().organizationNameSnapshot(),
                item.change().fromParentIdSnapshot(), item.change().reason(),
                item.change().executionStatus(), item.change().failureReason(), item.task().status(),
                item.task().submittedBy(), item.task().submittedAt(), item.task().reviewedBy(),
                item.task().reviewedAt(), item.task().reviewComment(), item.change().createdAt()
        );
    }
}
