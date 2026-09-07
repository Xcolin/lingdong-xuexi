package com.lingdong.learning.auth.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.ParentMobileChangeTicket;
import com.lingdong.learning.auth.application.ParentMobileChangeTicketStore;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/** Redis 保存手机号变更票据，并通过原子读取删除保证只能成功消费一次。 */
@Component
@Profile("!test")
public class RedisParentMobileChangeTicketStore implements ParentMobileChangeTicketStore {
    private static final String PREFIX = "auth:parent:mobile-change-ticket:";
    private static final DefaultRedisScript<String> GET_DELETE_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then redis.call('DEL', KEYS[1]) end
            return value
            """, String.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisParentMobileChangeTicketStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String ticketDigest, ParentMobileChangeTicket ticket, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(PREFIX + ticketDigest, objectMapper.writeValueAsString(ticket), ttl);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public ParentMobileChangeTicket find(String ticketDigest) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + ticketDigest);
            return value == null ? null : objectMapper.readValue(value, ParentMobileChangeTicket.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }

    @Override
    public ParentMobileChangeTicket consume(String ticketDigest) {
        try {
            String value = redisTemplate.execute(GET_DELETE_SCRIPT, Collections.singletonList(PREFIX + ticketDigest));
            return value == null ? null : objectMapper.readValue(value, ParentMobileChangeTicket.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            throw new AuthProtectionUnavailableException(exception);
        }
    }
}
