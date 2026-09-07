package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 副家长绑定或主家长转移邀请的持久化快照。 */
public record ParentRelationshipInvitation(
        Long id,
        Long studentId,
        Long inviterUserId,
        Long inviteeUserId,
        String inviteeMobile,
        ParentRelationshipInvitationType invitationType,
        ParentRelationshipInvitationStatus status,
        String pendingScopeKey,
        LocalDateTime expiresAt,
        LocalDateTime respondedAt,
        Long respondedByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
