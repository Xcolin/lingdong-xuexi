package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.IssuedParentBindingInvitation;
import com.lingdong.learning.student.domain.ParentBindingInvitation;

import java.time.LocalDateTime;

/** 创建邀请的响应；acceptToken 仅在本响应返回一次。 */
public record ParentBindingInvitationResponse(
        String id,
        String studentId,
        String organizationId,
        String status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        String acceptToken
) {
    public static ParentBindingInvitationResponse from(IssuedParentBindingInvitation issuedInvitation) {
        ParentBindingInvitation invitation = issuedInvitation.invitation();
        return new ParentBindingInvitationResponse(
                invitation.id().toString(), invitation.studentId().toString(), invitation.organizationId().toString(),
                invitation.status().name(), invitation.expiresAt(), invitation.createdAt(), issuedInvitation.acceptToken()
        );
    }
}
