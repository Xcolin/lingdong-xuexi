package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.captcha.CaptchaImageGenerator;
import com.lingdong.learning.auth.infrastructure.captcha.GeneratedCaptchaImage;
import com.lingdong.learning.auth.infrastructure.config.StudentLoginProtectionProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/** 生成、绑定并一次性验证学生登录图形验证码。 */
@Service
public class CaptchaChallengeService {
    private final CaptchaImageGenerator imageGenerator;
    private final StudentLoginProtectionStore protectionStore;
    private final SessionTokenService tokenService;
    private final StudentLoginProtectionProperties properties;
    private final Clock clock;

    public CaptchaChallengeService(
            CaptchaImageGenerator imageGenerator,
            StudentLoginProtectionStore protectionStore,
            SessionTokenService tokenService,
            StudentLoginProtectionProperties properties,
            Clock clock
    ) {
        this.imageGenerator = imageGenerator;
        this.protectionStore = protectionStore;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedCaptchaChallenge issue(String studentAccount, String deviceId) {
        String accountDigest = digestRequired(studentAccount, "学生账号");
        String deviceDigest = digestRequired(deviceId, "设备标识");
        protectionStore.checkCaptchaIssueRate(tokenService.hash(accountDigest + ":" + deviceDigest));

        GeneratedCaptchaImage generated = imageGenerator.generate();
        String challengeId = tokenService.newToken();
        Instant expiresAt = clock.instant().plus(properties.getCaptchaTtl());
        StoredCaptchaChallenge stored = new StoredCaptchaChallenge(
                answerHash(generated.answer()), accountDigest, deviceDigest, expiresAt
        );
        protectionStore.saveCaptcha(challengeId, stored, properties.getCaptchaTtl());
        return new IssuedCaptchaChallenge(challengeId, generated.imageBase64(), expiresAt);
    }

    public void verify(
            String challengeId,
            String answer,
            String studentAccount,
            String deviceId
    ) {
        if (challengeId == null || challengeId.isBlank() || answer == null || answer.isBlank()) {
            throw new CaptchaRequiredException();
        }
        StoredCaptchaChallenge challenge = protectionStore.consumeCaptcha(challengeId);
        if (challenge == null || !challenge.expiresAt().isAfter(clock.instant())) {
            throw new CaptchaRequiredException();
        }
        boolean accountMatches = constantTimeEquals(challenge.accountDigest(), digestRequired(studentAccount, "学生账号"));
        boolean deviceMatches = constantTimeEquals(challenge.deviceDigest(), digestRequired(deviceId, "设备标识"));
        boolean answerMatches = constantTimeEquals(challenge.answerHash(), answerHash(answer));
        if (!accountMatches || !deviceMatches || !answerMatches) {
            throw new CaptchaRequiredException();
        }
    }

    private String digestRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return tokenService.hash(value.trim());
    }

    private String answerHash(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return tokenService.hash("invalid-captcha-answer");
        }
        return tokenService.hash(answer.trim().toUpperCase(Locale.ROOT));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }
}
