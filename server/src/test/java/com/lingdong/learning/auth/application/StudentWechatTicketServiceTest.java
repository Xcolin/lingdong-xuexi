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

class StudentWechatTicketServiceTest {
    private final SessionTokenService tokenService = mock(SessionTokenService.class);
    private final ParentWechatProperties properties = new ParentWechatProperties();

    @Test
    void identityTicketStoresOnlyDigestWithoutWechatSessionKeyAndConsumesOnce() {
        IdentityMapStore store = new IdentityMapStore();
        when(tokenService.newToken()).thenReturn("student-wechat-ticket");
        when(tokenService.hash("student-wechat-ticket")).thenReturn("student-ticket-digest");
        StudentWechatIdentityTicketService service =
                new StudentWechatIdentityTicketService(store, tokenService, properties);

        String ticket = service.issue(new ParentWechatIdentity(
                "wx-app", "open-id", "union-id", "session-key"));

        assertThat(ticket).isEqualTo("student-wechat-ticket");
        assertThat(store.values).containsKey("student-ticket-digest").doesNotContainKey(ticket);
        assertThat(store.values.get("student-ticket-digest").sessionKey()).isNull();
        assertThat(store.ttl).isEqualTo(Duration.ofMinutes(5));
        assertThat(service.find(ticket).openId()).isEqualTo("open-id");
        assertThat(service.find(ticket).openId()).isEqualTo("open-id");
        assertThat(service.consume(ticket).openId()).isEqualTo("open-id");
        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(StudentWechatTicketInvalidException.class);
    }

    @Test
    void verificationTicketPinsStudentParentMobileAndDeviceAndConsumesOnce() {
        VerificationMapStore store = new VerificationMapStore();
        when(tokenService.newToken()).thenReturn("student-verification-ticket");
        when(tokenService.hash("student-verification-ticket")).thenReturn("verification-digest");
        StudentWechatVerificationTicketService service =
                new StudentWechatVerificationTicketService(store, tokenService, properties);
        StudentWechatVerificationTicket verification = new StudentWechatVerificationTicket(
                new ParentWechatIdentity("wx-app", "open-id", null, null),
                1874244142494647001L, 1874244142494647002L,
                1874244142494647003L, "13800000000", "mini-device");

        String ticket = service.issue(verification);

        assertThat(ticket).isEqualTo("student-verification-ticket");
        assertThat(store.values).containsEntry("verification-digest", verification).doesNotContainKey(ticket);
        assertThat(service.consume(ticket)).isEqualTo(verification);
        assertThatThrownBy(() -> service.consume(ticket))
                .isInstanceOf(StudentWechatTicketInvalidException.class);
    }

    private static final class IdentityMapStore implements StudentWechatIdentityTicketStore {
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

        @Override
        public ParentWechatIdentity find(String ticketDigest) {
            return values.get(ticketDigest);
        }
    }

    private static final class VerificationMapStore implements StudentWechatVerificationTicketStore {
        private final Map<String, StudentWechatVerificationTicket> values = new HashMap<>();

        @Override
        public void save(String ticketDigest, StudentWechatVerificationTicket ticket, Duration ttl) {
            values.put(ticketDigest, ticket);
        }

        @Override
        public StudentWechatVerificationTicket consume(String ticketDigest) {
            return values.remove(ticketDigest);
        }
    }
}
