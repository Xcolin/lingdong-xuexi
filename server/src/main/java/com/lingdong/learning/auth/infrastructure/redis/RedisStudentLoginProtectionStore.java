package com.lingdong.learning.auth.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.RateLimitedException;
import com.lingdong.learning.auth.application.StoredCaptchaChallenge;
import com.lingdong.learning.auth.application.StudentLoginProtectionStore;
import com.lingdong.learning.auth.infrastructure.config.StudentLoginProtectionProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/** 使用 Redis 原子脚本保存生产环境验证码挑战和固定窗口限流计数。 */
@Component
@Profile("!test")
public class RedisStudentLoginProtectionStore implements StudentLoginProtectionStore {
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);
    private static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StudentLoginProtectionProperties properties;

    public RedisStudentLoginProtectionStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StudentLoginProtectionProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void checkLoginRate(String accountDeviceDigest, String sourceDigest) {
        increment("auth:student:login:account-device:" + accountDeviceDigest,
                properties.getAccountDeviceLoginRequestsPerWindow(), properties.getLoginRateWindow());
        increment("auth:student:login:source:" + sourceDigest,
                properties.getSourceLoginRequestsPerWindow(), properties.getLoginRateWindow());
    }

    @Override
    public void checkCaptchaIssueRate(String accountDeviceDigest) {
        increment("auth:student:captcha-rate:" + accountDeviceDigest,
                properties.getCaptchaRequestsPerWindow(), properties.getCaptchaRateWindow());
    }

    @Override
    public void saveCaptcha(String challengeId, StoredCaptchaChallenge challenge, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    "auth:student:captcha:" + challengeId, objectMapper.writeValueAsString(challenge), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public StoredCaptchaChallenge consumeCaptcha(String challengeId) {
        try {
            String value = redisTemplate.execute(
                    CONSUME_SCRIPT, Collections.singletonList("auth:student:captcha:" + challengeId));
            return value == null ? null : objectMapper.readValue(value, StoredCaptchaChallenge.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    private void increment(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_SCRIPT, Collections.singletonList(key), String.valueOf(window.toMillis()));
            if (count == null) {
                throw new IllegalStateException("Redis限流脚本未返回计数");
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
}
