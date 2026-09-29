package com.lingdong.learning.learningtask.application;

/** 审核写操作完成后的稳定响应。 */
public record TaskReviewActionResult(
        Long assignmentId,
        String currentStatus,
        Long checkInId,
        String checkInStatus
) {
}
