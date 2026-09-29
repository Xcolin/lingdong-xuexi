package com.lingdong.learning.auth.infrastructure.redis;

import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.ParentSmsVerificationStore;
import com.lingdong.learning.auth.application.RateLimitedException;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/** 使用 Redis 原子脚本执行家长短信验证码限流和单次消费。 */
@Component
@Profile("!test")
public class RedisParentSmsVerificationStore implements ParentSmsVerificationStore {
    private static final String PREFIX = "auth:parent:sms:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);
    private static final DefaultRedisScript<Long> COMPARE_DELETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value and value == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ParentSmsProperties properties;

    public RedisParentSmsVerificationStore(StringRedisTemplate redisTemplate, ParentSmsProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void checkIssueRate(
            String mobileDigest,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String sourceDigest
    ) {
        increment(PREFIX + "issue:" + key(mobileDigest, purpose, clientType),
                properties.getIssuesPerWindow(), properties.getIssueRateWindow());
        increment(PREFIX + "issue-source:" + sourceDigest,
                properties.getSourceIssuesPerWindow(), properties.getIssueRateWindow());
    }

    @Override
    public void checkVerificationRate(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType) {
        increment(PREFIX + "verify:" + key(mobileDigest, purpose, clientType),
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
        try {
            redisTemplate.opsForValue().set(PREFIX + "code:" + key(mobileDigest, purpose, clientType), codeDigest, ttl);
        } catch (RuntimeException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public boolean consumeIfMatches(
            String mobileDigest,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String expectedCodeDigest
    ) {
        try {
            Long consumed = redisTemplate.execute(
                    COMPARE_DELETE_SCRIPT,
                    Collections.singletonList(PREFIX + "code:" + key(mobileDigest, purpose, clientType)),
                    expectedCodeDigest);
            return Long.valueOf(1L).equals(consumed);
        } catch (RuntimeException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public void remove(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType) {
        try {
            redisTemplate.delete(PREFIX + "code:" + key(mobileDigest, purpose, clientType));
        } catch (RuntimeException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    private void increment(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_SCRIPT, Collections.singletonList(key), String.valueOf(window.toMillis()));
            if (count == null) {
                throw new IllegalStateException("Redis 短信限流脚本未返回计数");
            }
            if (count > limit) {
                throw new RateLimitedException();
            }
        } catch (RateLimitedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    private String key(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType) {
        return mobileDigest + ":" + purpose.name() + ":" + clientType.name();
    }
}
