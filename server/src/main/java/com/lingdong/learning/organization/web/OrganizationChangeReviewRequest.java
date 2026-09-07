package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.Size;

/** 系统审核员的组织变更审批意见。 */
public record OrganizationChangeReviewRequest(
        @Size(max = 500) String comment
) {
}
