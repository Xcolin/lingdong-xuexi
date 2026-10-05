package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.organization.application.OrganizationChangeReviewItem;
import com.lingdong.learning.organization.domain.OrganizationChangeExecutionStatus;
import com.lingdong.learning.organization.domain.OrganizationChangeType;

import java.time.LocalDateTime;
import java.util.function.Function;

/** 组织变更申请快照、执行状态与审核状态的统一响应；申请人/审核人展示为姓名。 */
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
        String submittedBy,
        LocalDateTime submittedAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment,
        LocalDateTime createdAt
) {
    static OrganizationChangeResponse from(OrganizationChangeReviewItem item, Function<Long, String> nameOf) {
        return new OrganizationChangeResponse(
                item.change().id(), item.change().taskId(), item.change().organizationId(),
                item.change().changeType(), item.change().targetParentId(), item.change().expectedVersion(),
                item.change().organizationCodeSnapshot(), item.change().organizationNameSnapshot(),
                item.change().fromParentIdSnapshot(), item.change().reason(),
                item.change().executionStatus(), item.change().failureReason(), item.task().status(),
                nameOf.apply(item.task().submittedBy()), item.task().submittedAt(), nameOf.apply(item.task().reviewedBy()),
                item.task().reviewedAt(), item.task().reviewComment(), item.change().createdAt()
        );
    }
}
