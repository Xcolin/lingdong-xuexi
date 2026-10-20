package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.ParentRelationshipInvitationType;

import java.time.LocalDateTime;

/** 家长收到的待处理关系邀请。 */
public record ParentReceivedInvitationView(
        Long invitationId,
        String studentName,
        String inviterName,
        ParentRelationshipInvitationType invitationType,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
