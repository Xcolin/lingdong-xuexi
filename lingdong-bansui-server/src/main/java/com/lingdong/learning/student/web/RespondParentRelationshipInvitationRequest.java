package com.lingdong.learning.student.web;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 公开响应家长关系邀请的验证码与独立设备信息。 */
public record RespondParentRelationshipInvitationRequest(
        String mobile,
        String smsCode,
        AuthClientType clientType,
        String deviceId,
        String deviceName,
        boolean agreementAccepted,
        String agreementVersion
) {
}
