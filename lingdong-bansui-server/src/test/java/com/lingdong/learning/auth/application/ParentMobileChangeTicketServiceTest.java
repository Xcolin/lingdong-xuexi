package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParentMobileChangeTicketServiceTest {
    private final MapStore store = new MapStore();
    private final SessionTokenService tokenService = mock(SessionTokenService.class);
    private final ParentSmsProperties properties = new ParentSmsProperties();

    @Test
    void storesOnlyDigestAndBindsTicketToCurrentVerificationContext() {
        when(tokenService.newToken()).thenReturn("opaque-mobile-change-ticket");
        when(tokenService.hash("opaque-mobile-change-ticket")).thenReturn("ticket-sha256");
        ParentMobileChangeTicketService service =
                new ParentMobileChangeTicketService(store, tokenService, properties);

        String ticket = service.issue(1001L, AuthClientType.MINIAPP, "current-mobile-digest");

        assertThat(ticket).isEqualTo("opaque-mobile-change-ticket");
        assertThat(store.values).containsKey("ticket-sha256").doesNotContainKey(ticket);
        assertThat(store.ttl).isEqualTo(Duration.ofMinutes(5));
        assertThat(service.requireValid(
                ticket, 1001L, AuthClientType.MINIAPP, "current-mobile-digest").userId()).isEqualTo(1001L);
    }

    @Test
    void rejectsMismatchedContextAndAllowsOnlyOneSuccessfulConsumption() {
        when(tokenService.newToken()).thenReturn("opaque-mobile-change-ticket");
        when(tokenService.hash("opaque-mobile-change-ticket")).thenReturn("ticket-sha256");
        ParentMobileChangeTicketService service =
                new ParentMobileChangeTicketService(store, tokenService, properties);
        String ticket = service.issue(1001L, AuthClientType.WEB, "current-mobile-digest");

        assertThatThrownBy(() -> service.requireValid(
                ticket, 1002L, AuthClientType.WEB, "current-mobile-digest"))
                .isInstanceOf(ParentMobileChangeTicketInvalidException.class);
        assertThatThrownBy(() -> service.requireValid(
                ticket, 1001L, AuthClientType.MINIAPP, "current-mobile-digest"))
                .isInstanceOf(ParentMobileChangeTicketInvalidException.class);
        assertThatThrownBy(() -> service.requireValid(
                ticket, 1001L, AuthClientType.WEB, "another-mobile-digest"))
                .isInstanceOf(ParentMobileChangeTicketInvalidException.class);

        assertThat(service.consume(
                ticket, 1001L, AuthClientType.WEB, "current-mobile-digest").userId()).isEqualTo(1001L);
        assertThatThrownBy(() -> service.consume(
                ticket, 1001L, AuthClientType.WEB, "current-mobile-digest"))
                .isInstanceOf(ParentMobileChangeTicketInvalidException.class);
    }

    private static final class MapStore implements ParentMobileChangeTicketStore {
        private final Map<String, ParentMobileChangeTicket> values = new HashMap<>();
        private Duration ttl;

        @Override
        public void save(String ticketDigest, ParentMobileChangeTicket ticket, Duration ttl) {
            values.put(ticketDigest, ticket);
            this.ttl = ttl;
        }

        @Override
        public ParentMobileChangeTicket find(String ticketDigest) {
            return values.get(ticketDigest);
        }

        @Override
        public ParentMobileChangeTicket consume(String ticketDigest) {
            return values.remove(ticketDigest);
        }
    }
}
