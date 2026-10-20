package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.ParentReceivedInvitationView;

/** 家长收到的待处理关系邀请响应。 */
public record ParentReceivedInvitationResponse(
        String invitationId,
        String studentName,
        String inviterName,
        String invitationType,
        String createdAt,
        String expiresAt
) {
    static ParentReceivedInvitationResponse from(ParentReceivedInvitationView view) {
        return new ParentReceivedInvitationResponse(
                view.invitationId().toString(),
                view.studentName(),
                view.inviterName(),
                view.invitationType().name(),
                view.createdAt() == null ? null : view.createdAt().toString(),
                view.expiresAt() == null ? null : view.expiresAt().toString());
    }
}
