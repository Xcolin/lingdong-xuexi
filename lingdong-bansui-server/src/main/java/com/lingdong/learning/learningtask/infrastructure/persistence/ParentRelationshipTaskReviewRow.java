package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 主家长关系变更时被锁定的家庭待审核任务。 */
public record ParentRelationshipTaskReviewRow(
        Long assignmentId,
        Long currentReviewerId
) {
}
