package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AccountSecurityEvent;
import com.lingdong.learning.auth.domain.AccountSecurityEventStatus;
import com.lingdong.learning.auth.domain.AccountSecurityEventType;
import com.lingdong.learning.auth.domain.AccountSecurityRiskLevel;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.AccountSecurityEventMapper;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountSecurityEventPersistenceTest {
    private static final long USER_ID = 8920000000000000001L;
    private static final long OTHER_USER_ID = 8920000000000000002L;
    private static final long ACTIVE_SESSION_ID = 8920000000000000003L;
    private static final long EXPIRED_SESSION_ID = 8920000000000000004L;
    private static final long EVENT_ID = 8920000000000000005L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccountSecurityEventMapper eventMapper;
    @Autowired private DeviceSessionMapper sessionMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'security_event_owner', '安全事件用户', 'PLATFORM', 'ENABLED'),
                       (?, 'security_event_other', '其他安全事件用户', 'PLATFORM', 'ENABLED')
                """, USER_ID, OTHER_USER_ID);
        insertSession(ACTIVE_SESSION_ID, "known-device", "已知浏览器", "a", "b",
                LocalDateTime.now().plusDays(7));
        insertSession(EXPIRED_SESSION_ID, "expired-device", "过期浏览器", "c", "d",
                LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void insertsUniqueDeviceEventAndMarksOnlyOwnedEventsRead() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 9, 15, 30);
        AccountSecurityEvent event = new AccountSecurityEvent(
                EVENT_ID, USER_ID, ACTIVE_SESSION_ID,
                AccountSecurityEventType.NEW_DEVICE_LOGIN,
                AccountSecurityRiskLevel.WARNING, AuthClientType.WEB,
                "已知浏览器", "e".repeat(64), "DEVICE:" + "e".repeat(64),
                AccountSecurityEventStatus.UNREAD, occurredAt, null, null, null);

        assertThat(eventMapper.insertIfAbsent(event)).isEqualTo(1);
        assertThat(eventMapper.insertIfAbsent(new AccountSecurityEvent(
                EVENT_ID + 1, USER_ID, ACTIVE_SESSION_ID,
                AccountSecurityEventType.NEW_DEVICE_LOGIN,
                AccountSecurityRiskLevel.WARNING, AuthClientType.WEB,
                "重复浏览器", "e".repeat(64), "DEVICE:" + "e".repeat(64),
                AccountSecurityEventStatus.UNREAD, occurredAt.plusSeconds(1), null, null, null)))
                .isZero();

        assertThat(eventMapper.findRecentByUser(USER_ID, true, 50)).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo(EVENT_ID);
            assertThat(saved.riskLevel()).isEqualTo(AccountSecurityRiskLevel.WARNING);
            assertThat(saved.status()).isEqualTo(AccountSecurityEventStatus.UNREAD);
        });
        assertThat(eventMapper.findByIdAndUserId(EVENT_ID, OTHER_USER_ID)).isNull();
        assertThat(eventMapper.markReadIfUnread(
                EVENT_ID, OTHER_USER_ID, occurredAt.plusMinutes(1))).isZero();
        assertThat(eventMapper.markReadIfUnread(
                EVENT_ID, USER_ID, occurredAt.plusMinutes(1))).isEqualTo(1);
        assertThat(eventMapper.markReadIfUnread(
                EVENT_ID, USER_ID, occurredAt.plusMinutes(2))).isZero();
        assertThat(eventMapper.findRecentByUser(USER_ID, true, 50)).isEmpty();
        assertThat(eventMapper.findRecentByUser(USER_ID, false, 50)).singleElement()
                .satisfies(saved -> assertThat(saved.status()).isEqualTo(AccountSecurityEventStatus.READ));
        assertThat(eventMapper.markAllRead(USER_ID, occurredAt.plusMinutes(3))).isZero();
    }

    @Test
    void recognizesHistoricalDeviceAndListsOnlyRefreshableActiveSessions() {
        LocalDateTime now = LocalDateTime.now();

        assertThat(sessionMapper.existsByUserClientAndDevice(
                USER_ID, AuthClientType.WEB, "known-device")).isTrue();
        assertThat(sessionMapper.existsByUserClientAndDevice(
                USER_ID, AuthClientType.MINIAPP, "known-device")).isFalse();
        assertThat(sessionMapper.findActiveByUserId(USER_ID, now)).singleElement()
                .satisfies(session -> assertThat(session.id()).isEqualTo(ACTIVE_SESSION_ID));
    }

    private void insertSession(
            long sessionId,
            String deviceId,
            String deviceName,
            String accessHashCharacter,
            String refreshHashCharacter,
            LocalDateTime refreshExpiresAt
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into auth_device_session (
                    id, user_id, client_type, device_id, device_name,
                    access_token_hash, refresh_token_hash, access_expires_at,
                    refresh_expires_at, status, last_active_at
                ) values (?, ?, 'WEB', ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, sessionId, USER_ID, deviceId, deviceName,
                accessHashCharacter.repeat(64), refreshHashCharacter.repeat(64),
                now.plusMinutes(30), refreshExpiresAt, now);
    }
}
