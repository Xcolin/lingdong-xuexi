package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.ParentPhoneAuthenticatedSession;

/** 家长登录后的会话与首次引导状态响应。 */
public record ParentSessionResponse(
        SessionResponse session,
        boolean onboardingRequired,
        boolean agreementAcceptanceRequired,
        String currentAgreementVersion
) {
    public static ParentSessionResponse from(ParentPhoneAuthenticatedSession authenticated) {
        var session = authenticated.session();
        return new ParentSessionResponse(
                new SessionResponse(session.sessionId(), session.accessToken(), session.refreshToken(),
                        session.accessExpiresAt(), session.refreshExpiresAt()),
                authenticated.onboardingRequired(),
                authenticated.agreementAcceptanceRequired(),
                authenticated.currentAgreementVersion());
    }
}
