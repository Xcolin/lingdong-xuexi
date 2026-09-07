package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentWechatIdentity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryParentWechatBindingTicketStoreTest {
    private static final ParentWechatIdentity IDENTITY =
            new ParentWechatIdentity("wx-app", "open-id", null, null);

    @Test
    void expiresTicketsAndConsumesValidTicketOnlyOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC);
        InMemoryParentWechatBindingTicketStore store = new InMemoryParentWechatBindingTicketStore(clock);
        store.save("expired", IDENTITY, Duration.ZERO);
        store.save("valid", IDENTITY, Duration.ofMinutes(5));

        assertThat(store.consume("expired")).isNull();
        assertThat(store.consume("valid")).isEqualTo(IDENTITY);
        assertThat(store.consume("valid")).isNull();
    }
}
