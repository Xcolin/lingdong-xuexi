package com.lingdong.learning.auth.application;

/** 已认证家长进入业务页面前必须遵循的服务端状态。 */
public record ParentAuthState(
        boolean onboardingRequired,
        boolean agreementAcceptanceRequired,
        String currentAgreementVersion
) {
}
