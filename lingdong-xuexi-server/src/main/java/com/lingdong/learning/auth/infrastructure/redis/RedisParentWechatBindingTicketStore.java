package com.lingdong.learning.auth.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.ParentWechatBindingTicketStore;
import com.lingdong.learning.auth.application.ParentWechatIdentity;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/** 使用 Redis 原子读取删除保证微信绑定票据只能成功消费一次。 */
@Component
@Profile("!test")
public class RedisParentWechatBindingTicketStore implements ParentWechatBindingTicketStore {
    private static final String PREFIX = "auth:parent:wechat:binding-ticket:";
    private static final DefaultRedisScript<String> GET_DELETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisParentWechatBindingTicketStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String ticketDigest, ParentWechatIdentity identity, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(PREFIX + ticketDigest, objectMapper.writeValueAsString(identity), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public ParentWechatIdentity consume(String ticketDigest) {
        try {
            String value = redisTemplate.execute(
                    GET_DELETE_SCRIPT, Collections.singletonList(PREFIX + ticketDigest));
            return value == null ? null : objectMapper.readValue(value, ParentWechatIdentity.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }
}
