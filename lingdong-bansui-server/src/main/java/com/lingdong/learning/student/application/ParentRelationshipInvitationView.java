package com.lingdong.learning.student.application;

import java.time.LocalDateTime;

/** 创建邀请后允许返回给调用端的脱敏信息。 */
public record ParentRelationshipInvitationView(
        Long invitationId,
        String maskedMobile,
        LocalDateTime expiresAt
) {
}
