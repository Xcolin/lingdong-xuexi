package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.RateLimitedException;
import com.lingdong.learning.auth.application.StoredCaptchaChallenge;
import com.lingdong.learning.auth.application.StudentLoginProtectionStore;
import com.lingdong.learning.auth.infrastructure.config.StudentLoginProtectionProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境风控存储，不建立任何 Redis 网络连接。 */
@Component
@Profile("test")
public class InMemoryStudentLoginProtectionStore implements StudentLoginProtectionStore {
    private final StudentLoginProtectionProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, StoredCaptchaChallenge> captchas = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CounterWindow> counters = new ConcurrentHashMap<>();

    public InMemoryStudentLoginProtectionStore(StudentLoginProtectionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void checkLoginRate(String accountDeviceDigest, String sourceDigest) {
        increment("login-account:" + accountDeviceDigest,
                properties.getAccountDeviceLoginRequestsPerWindow(), properties.getLoginRateWindow());
        increment("login-source:" + sourceDigest,
                properties.getSourceLoginRequestsPerWindow(), properties.getLoginRateWindow());
    }

    @Override
    public void checkCaptchaIssueRate(String accountDeviceDigest) {
        increment("captcha:" + accountDeviceDigest,
                properties.getCaptchaRequestsPerWindow(), properties.getCaptchaRateWindow());
    }

    @Override
    public void saveCaptcha(String challengeId, StoredCaptchaChallenge challenge, Duration ttl) {
        captchas.put(challengeId, challenge);
    }

    @Override
    public StoredCaptchaChallenge consumeCaptcha(String challengeId) {
        StoredCaptchaChallenge challenge = captchas.remove(challengeId);
        return challenge != null && challenge.expiresAt().isAfter(clock.instant()) ? challenge : null;
    }

    private void increment(String key, int limit, Duration windowDuration) {
        Instant now = clock.instant();
        CounterWindow window = counters.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new CounterWindow(1, now.plus(windowDuration));
            }
            return new CounterWindow(existing.count() + 1, existing.expiresAt());
        });
        if (window.count() > limit) {
            throw new RateLimitedException();
        }
    }

    private record CounterWindow(int count, Instant expiresAt) {
    }
}
