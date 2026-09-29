package com.lingdong.learning.student.application;

/** 家长接受或拒绝邀请时提交的一次性令牌。 */
public record RespondParentBindingInvitationCommand(Long invitationId, String acceptToken) {
}
