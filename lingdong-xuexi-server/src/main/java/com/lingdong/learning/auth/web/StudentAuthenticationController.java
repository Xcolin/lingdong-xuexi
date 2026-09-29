package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.CaptchaChallengeService;
import com.lingdong.learning.auth.application.StudentCodeLoginApplicationService;
import com.lingdong.learning.auth.application.StudentCodeLoginCommand;
import com.lingdong.learning.auth.application.StudentQrLoginApplicationService;
import com.lingdong.learning.auth.application.StudentQrLoginCommand;
import com.lingdong.learning.feature.application.FeatureAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 小程序学生登录的公开预认证入口。 */
@RestController
@RequestMapping("/api/v1/auth")
public class StudentAuthenticationController {
    private static final String FEATURE_CODE = "STUDENT_CODE_LOGIN";

    private final StudentCodeLoginApplicationService loginService;
    private final CaptchaChallengeService captchaChallengeService;
    private final FeatureAccessService featureAccessService;
    private final StudentQrLoginApplicationService qrLoginService;

    public StudentAuthenticationController(
            StudentCodeLoginApplicationService loginService,
            CaptchaChallengeService captchaChallengeService,
            FeatureAccessService featureAccessService,
            StudentQrLoginApplicationService qrLoginService
    ) {
        this.loginService = loginService;
        this.captchaChallengeService = captchaChallengeService;
        this.featureAccessService = featureAccessService;
        this.qrLoginService = qrLoginService;
    }

    @PostMapping("/student-captchas")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentCaptchaResponse issueCaptcha(@Valid @RequestBody StudentCaptchaRequest request) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        return StudentCaptchaResponse.from(
                captchaChallengeService.issue(request.studentAccount(), request.deviceId()));
    }

    @PostMapping("/student-sessions/code")
    public SessionResponse loginByCode(
            @RequestBody StudentCodeLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedSession session = loginService.login(new StudentCodeLoginCommand(
                request.studentAccount(), request.loginCode(), request.deviceId(), request.deviceName(),
                request.captchaChallengeId(), request.captchaAnswer(), servletRequest.getRemoteAddr()
        ));
        return new SessionResponse(session.sessionId(), session.accessToken(), session.refreshToken(),
                session.accessExpiresAt(), session.refreshExpiresAt());
    }

    @PostMapping("/student-sessions/qr")
    public StudentQrSessionResponse loginByQr(
            @Valid @RequestBody StudentQrLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return StudentQrSessionResponse.from(qrLoginService.login(new StudentQrLoginCommand(
                request.qrContent(), request.loginCode(), request.deviceId(), request.deviceName(),
                request.captchaChallengeId(), request.captchaAnswer(), servletRequest.getRemoteAddr())));
    }

    @PostMapping("/student-qr-captchas")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentCaptchaResponse issueQrCaptcha(@Valid @RequestBody StudentQrCaptchaRequest request) {
        return StudentCaptchaResponse.from(qrLoginService.issueCaptcha(request.qrContent(), request.deviceId()));
    }
}
