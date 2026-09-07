package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 被邀请家长使用目标手机号和短信验证码响应关系邀请的命令。 */
public record RespondParentRelationshipInvitationCommand(
        Long invitationId,
        String mobile,
        String smsCode,
        AuthClientType clientType,
        boolean agreementAccepted,
        String agreementVersion,
        String sourceAddressHash
) {
}
