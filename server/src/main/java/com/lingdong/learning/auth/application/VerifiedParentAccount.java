package com.lingdong.learning.auth.application;

/** 手机号验证后可参与微信绑定的唯一家长账号与准入状态。 */
public record VerifiedParentAccount(
        Long userId,
        boolean onboardingRequired,
        String currentAgreementVersion
) {
}
