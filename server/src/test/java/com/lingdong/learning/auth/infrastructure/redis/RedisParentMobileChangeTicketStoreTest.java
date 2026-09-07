package com.lingdong.learning.auth.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisParentMobileChangeTicketStoreTest {
    @Test
    void failsClosedWhenRedisCannotConsumeTicketAtomically() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RedisParentMobileChangeTicketStore store =
                new RedisParentMobileChangeTicketStore(redisTemplate, new ObjectMapper());

        assertThatThrownBy(() -> store.consume("ticket-digest"))
                .isInstanceOf(AuthProtectionUnavailableException.class);
    }
}
