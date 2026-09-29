package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentMobileChangeTicket;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryParentMobileChangeTicketStoreTest {
    private static final ParentMobileChangeTicket TICKET =
            new ParentMobileChangeTicket(1001L, AuthClientType.MINIAPP, "mobile-digest");

    @Test
    void expiresTicketsAndConsumesValidTicketOnlyOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);
        InMemoryParentMobileChangeTicketStore store = new InMemoryParentMobileChangeTicketStore(clock);
        store.save("expired", TICKET, Duration.ZERO);
        store.save("valid", TICKET, Duration.ofMinutes(5));

        assertThat(store.find("expired")).isNull();
        assertThat(store.find("valid")).isEqualTo(TICKET);
        assertThat(store.consume("valid")).isEqualTo(TICKET);
        assertThat(store.consume("valid")).isNull();
    }
}
