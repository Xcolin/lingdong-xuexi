package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentWechatProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParentWechatBindingTicketServiceTest {
    private final MapStore store = new MapStore();
    private final SessionTokenService tokenService = mock(SessionTokenService.class);
    private final ParentWechatProperties properties = new ParentWechatProperties();

    @Test
    void storesOnlyTicketDigestAndConsumesIdentityOnce() {
        when(tokenService.newToken()).thenReturn("opaque-binding-ticket");
        when(tokenService.hash("opaque-binding-ticket")).thenReturn("ticket-sha256");
        ParentWechatBindingTicketService service =
                new ParentWechatBindingTicketService(store, tokenService, properties);
        ParentWechatIdentity identity = new ParentWechatIdentity(
                "wx-app", "open-id", "union-id", "session-key-never-persisted");

        String ticket = service.issue(identity);

        assertThat(ticket).isEqualTo("opaque-binding-ticket");
        assertThat(store.values).containsKey("ticket-sha256").doesNotContainKey(ticket);
        assertThat(store.values.get("ticket-sha256").sessionKey()).isNull();
        assertThat(store.ttl).isEqualTo(Duration.ofMinutes(5));
        assertThat(service.consume(ticket).openId()).isEqualTo("open-id");
        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(ParentWechatBindingTicketInvalidException.class);
    }

    private static final class MapStore implements ParentWechatBindingTicketStore {
        private final Map<String, ParentWechatIdentity> values = new HashMap<>();
        private Duration ttl;

        @Override
        public void save(String ticketDigest, ParentWechatIdentity identity, Duration ttl) {
            values.put(ticketDigest, identity);
            this.ttl = ttl;
        }

        @Override
        public ParentWechatIdentity consume(String ticketDigest) {
            return values.remove(ticketDigest);
        }
    }
}
