package com.lingdong.learning.learningtask.application;

/** 审核通过、任务完成和积分入账的原子事务结果。 */
public record ApproveTaskReviewResult(
        Long assignmentId,
        String currentStatus,
        Long checkInId,
        String checkInStatus,
        long awardedPoints,
        long totalPoints,
        long availablePoints,
        Long ledgerId
) {
}
