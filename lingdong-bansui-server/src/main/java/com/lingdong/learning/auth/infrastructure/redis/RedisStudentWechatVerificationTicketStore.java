package com.lingdong.learning.auth.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.StudentWechatVerificationTicket;
import com.lingdong.learning.auth.application.StudentWechatVerificationTicketStore;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/** 使用 Redis 原子读取删除保证学生微信最终校验票据只能消费一次。 */
@Component
@Profile("!test")
public class RedisStudentWechatVerificationTicketStore implements StudentWechatVerificationTicketStore {
    private static final String PREFIX = "auth:student:wechat:verification-ticket:";
    private static final DefaultRedisScript<String> GET_DELETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStudentWechatVerificationTicketStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String ticketDigest, StudentWechatVerificationTicket ticket, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(PREFIX + ticketDigest, objectMapper.writeValueAsString(ticket), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public StudentWechatVerificationTicket consume(String ticketDigest) {
        try {
            String value = redisTemplate.execute(GET_DELETE_SCRIPT, Collections.singletonList(PREFIX + ticketDigest));
            return value == null ? null : objectMapper.readValue(value, StudentWechatVerificationTicket.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }
}
