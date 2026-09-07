package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.ParentRelationshipInvitationView;

import java.time.LocalDateTime;

/** 不暴露完整手机号的家长关系邀请响应。 */
public record ParentRelationshipInvitationResponse(
        String invitationId,
        String maskedMobile,
        LocalDateTime expiresAt
) {
    public static ParentRelationshipInvitationResponse from(ParentRelationshipInvitationView view) {
        return new ParentRelationshipInvitationResponse(
                view.invitationId().toString(), view.maskedMobile(), view.expiresAt());
    }
}
