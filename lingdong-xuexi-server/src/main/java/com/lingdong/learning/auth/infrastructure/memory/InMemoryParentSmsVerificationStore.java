package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.ParentSmsVerificationStore;
import com.lingdong.learning.auth.application.RateLimitedException;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境短信验证码存储，保证测试不会连接远程 Redis。 */
@Component
@Profile("test")
public class InMemoryParentSmsVerificationStore implements ParentSmsVerificationStore {
    private final ParentSmsProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, StoredCode> codes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CounterWindow> counters = new ConcurrentHashMap<>();

    public InMemoryParentSmsVerificationStore(ParentSmsProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void checkIssueRate(
            String mobileDigest,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String sourceDigest
    ) {
        increment("issue:" + key(mobileDigest, purpose, clientType),
                properties.getIssuesPerWindow(), properties.getIssueRateWindow());
        increment("issue-source:" + sourceDigest,
                properties.getSourceIssuesPerWindow(), properties.getIssueRateWindow());
    }

    @Override
    public void checkVerificationRate(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType) {
        increment("verify:" + key(mobileDigest, purpose, clientType),
                properties.getVerificationsPerWindow(), properties.getVerificationRateWindow());
    }

    @Override
    public void save(
            String mobileDigest,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String codeDigest,
            Duration ttl
    ) {
        codes.put(key(mobileDigest, purpose, clientType), new StoredCode(codeDigest, clock.instant().plus(ttl)));
    }

    @Override
    public boolean consumeIfMatches(
            String mobileDigest,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String expectedCodeDigest
    ) {
        String key = key(mobileDigest, purpose, clientType);
        Instant now = clock.instant();
        boolean[] consumed = {false};
        codes.compute(key, (ignored, stored) -> {
            if (stored == null || !stored.expiresAt().isAfter(now)) {
                return null;
            }
            if (stored.codeDigest().equals(expectedCodeDigest)) {
                consumed[0] = true;
                return null;
            }
            return stored;
        });
        return consumed[0];
    }

    @Override
    public void remove(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType) {
        codes.remove(key(mobileDigest, purpose, clientType));
    }

    /** 清理测试上下文中的验证码和频控窗口，避免不同测试事务共享进程内状态。 */
    public void clear() {
        codes.clear();
        counters.clear();
    }

    private void increment(String key, int limit, Duration duration) {
        Instant now = clock.instant();
        CounterWindow window = counters.compute(key, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new CounterWindow(1, now.plus(duration));
            }
            return new CounterWindow(existing.count() + 1, existing.expiresAt());
        });
        if (window.count() > limit) {
            throw new RateLimitedException();
        }
    }

    private String key(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType) {
        return mobileDigest + ":" + purpose.name() + ":" + clientType.name();
    }

    private record StoredCode(String codeDigest, Instant expiresAt) {
    }

    private record CounterWindow(int count, Instant expiresAt) {
    }
}
