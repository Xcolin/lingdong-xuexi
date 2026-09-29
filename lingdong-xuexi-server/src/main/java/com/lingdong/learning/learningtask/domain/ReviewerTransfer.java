package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 一次审核责任转交记录。 */
public record ReviewerTransfer(
        Long id,
        Long assignmentId,
        Long fromReviewerUserId,
        Long toReviewerUserId,
        Long transferredByUserId,
        String transferReason,
        LocalDateTime transferredAt
) {
}
