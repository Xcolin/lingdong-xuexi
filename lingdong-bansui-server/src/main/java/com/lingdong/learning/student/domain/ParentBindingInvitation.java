package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 不包含原始令牌的家长绑定邀请持久化事实。 */
public record ParentBindingInvitation(
        Long id,
        Long studentId,
        Long organizationId,
        Long inviterUserId,
        String tokenHash,
        ParentBindingInvitationStatus status,
        String pendingScopeKey,
        LocalDateTime expiresAt,
        LocalDateTime respondedAt,
        Long respondedByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ParentBindingInvitation pending(
            Long id,
            Long studentId,
            Long organizationId,
            Long inviterUserId,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        return new ParentBindingInvitation(
                id, studentId, organizationId, inviterUserId, tokenHash, ParentBindingInvitationStatus.PENDING,
                "PENDING", expiresAt, null, null, null, null
        );
    }
}
