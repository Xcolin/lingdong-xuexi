package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 审核列表各状态计数行（按最新打卡状态分组）。 */
public record TaskReviewStatusCountRow(
        String checkinStatus,
        long cnt
) {
}
