package com.lingdong.learning.auth.application;

/** 家长登录会话及前端路由所需的协议、引导状态。 */
public record ParentPhoneAuthenticatedSession(
        AuthenticatedSession session,
        boolean onboardingRequired,
        boolean agreementAcceptanceRequired,
        String currentAgreementVersion
) {
}
