package com.lingdong.learning.organization.application;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.organization.domain.OrganizationChange;

/** 组织变更申请快照与系统审核状态的组合查询结果。 */
public record OrganizationChangeReviewItem(
        OrganizationChange change,
        SystemTask task
) {
}
