package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.captcha.CaptchaImageGenerator;
import com.lingdong.learning.auth.infrastructure.captcha.GeneratedCaptchaImage;
import com.lingdong.learning.auth.infrastructure.config.StudentLoginProtectionProperties;
import com.lingdong.learning.auth.infrastructure.memory.InMemoryStudentLoginProtectionStore;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptchaChallengeServiceTest {
    private static final String ACCOUNT = "26000001";
    private static final String DEVICE = "student-device";

    @Test
    void bindsCaptchaToAccountAndDeviceAndConsumesItOnce() {
        MutableClock clock = new MutableClock();
        CaptchaChallengeService service = service(clock);

        IssuedCaptchaChallenge challenge = service.issue(ACCOUNT, DEVICE);

        assertThat(challenge.imageBase64()).startsWith("data:image/png;base64,");
        service.verify(challenge.challengeId(), "aB12", ACCOUNT, DEVICE);
        assertThatThrownBy(() -> service.verify(challenge.challengeId(), "AB12", ACCOUNT, DEVICE))
                .isInstanceOf(CaptchaRequiredException.class);
    }

    @Test
    void rejectsCrossDeviceAndExpiredCaptcha() {
        MutableClock clock = new MutableClock();
        CaptchaChallengeService service = service(clock);
        IssuedCaptchaChallenge crossDevice = service.issue(ACCOUNT, DEVICE);

        assertThatThrownBy(() -> service.verify(crossDevice.challengeId(), "AB12", ACCOUNT, "other-device"))
                .isInstanceOf(CaptchaRequiredException.class);

        IssuedCaptchaChallenge expired = service.issue(ACCOUNT, DEVICE);
        clock.advance(Duration.ofMinutes(3));
        assertThatThrownBy(() -> service.verify(expired.challengeId(), "AB12", ACCOUNT, DEVICE))
                .isInstanceOf(CaptchaRequiredException.class);
    }

    @Test
    void rateLimitsCaptchaIssueByAccountAndDevice() {
        MutableClock clock = new MutableClock();
        CaptchaChallengeService service = service(clock);
        for (int attempt = 0; attempt < 10; attempt++) {
            service.issue(ACCOUNT, DEVICE);
        }

        assertThatThrownBy(() -> service.issue(ACCOUNT, DEVICE))
                .isInstanceOf(RateLimitedException.class);
    }

    private CaptchaChallengeService service(Clock clock) {
        StudentLoginProtectionProperties properties = new StudentLoginProtectionProperties();
        SessionTokenService tokenService = new SessionTokenService();
        InMemoryStudentLoginProtectionStore store = new InMemoryStudentLoginProtectionStore(properties, clock);
        CaptchaImageGenerator generator = () -> new GeneratedCaptchaImage("AB12", "data:image/png;base64,dGVzdA==");
        return new CaptchaChallengeService(generator, store, tokenService, properties, clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-01T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
