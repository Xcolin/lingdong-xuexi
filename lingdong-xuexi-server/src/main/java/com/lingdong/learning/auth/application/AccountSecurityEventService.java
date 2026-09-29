package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AccountSecurityEvent;
import com.lingdong.learning.auth.domain.AccountSecurityEventStatus;
import com.lingdong.learning.auth.domain.AccountSecurityEventType;
import com.lingdong.learning.auth.domain.AccountSecurityRiskLevel;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.domain.DeviceSessionRecord;
import com.lingdong.learning.auth.infrastructure.persistence.AccountSecurityEventMapper;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 记录和查询用户本人的站内账号安全事件。 */
@Service
public class AccountSecurityEventService {
    private static final int QUERY_LIMIT = 50;
    private static final String FEATURE_CODE = "ACCOUNT_SECURITY_MANAGEMENT";

    private final AccountSecurityEventMapper eventMapper;
    private final SessionTokenService tokenService;
    private final IdGenerator idGenerator;
    private final FeatureAccessService featureAccessService;

    public AccountSecurityEventService(
            AccountSecurityEventMapper eventMapper,
            SessionTokenService tokenService,
            IdGenerator idGenerator,
            FeatureAccessService featureAccessService
    ) {
        this.eventMapper = eventMapper;
        this.tokenService = tokenService;
        this.idGenerator = idGenerator;
        this.featureAccessService = featureAccessService;
    }

    /** 同一用户、客户端和设备标识仅记录一次首次设备提醒。 */
    public void recordNewDevice(
            Long userId,
            Long sessionId,
            AuthClientType clientType,
            String deviceId,
            String deviceName,
            LocalDateTime occurredAt
    ) {
        String deviceHash = tokenService.hash(deviceId);
        insert(new AccountSecurityEvent(
                idGenerator.nextId(), userId, sessionId,
                AccountSecurityEventType.NEW_DEVICE_LOGIN,
                AccountSecurityRiskLevel.WARNING, clientType, deviceName,
                deviceHash, "DEVICE:" + clientType.name() + ":" + deviceHash,
                AccountSecurityEventStatus.UNREAD,
                occurredAt, null, null, null));
    }

    public void recordDeviceRevoked(Long userId, DeviceSessionRecord session, LocalDateTime occurredAt) {
        recordInfo(userId, session, AccountSecurityEventType.DEVICE_REVOKED, occurredAt);
    }

    public void recordAllSessionsRevoked(Long userId, DeviceSessionRecord operatorSession, LocalDateTime occurredAt) {
        recordInfo(userId, operatorSession, AccountSecurityEventType.ALL_SESSIONS_REVOKED, occurredAt);
    }

    @Transactional(readOnly = true)
    public List<AccountSecurityEventView> findRecent(Long userId, boolean unreadOnly) {
        requireEnabled();
        return eventMapper.findRecentByUser(userId, unreadOnly, QUERY_LIMIT).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public void markRead(Long userId, Long eventId) {
        requireEnabled();
        if (eventMapper.findByIdAndUserId(eventId, userId) == null) {
            throw new ResourceNotFoundException("账号安全事件不存在");
        }
        eventMapper.markReadIfUnread(eventId, userId, LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(Long userId) {
        requireEnabled();
        eventMapper.markAllRead(userId, LocalDateTime.now());
    }

    private void requireEnabled() {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
    }

    private void recordInfo(
            Long userId,
            DeviceSessionRecord session,
            AccountSecurityEventType eventType,
            LocalDateTime occurredAt
    ) {
        long eventId = idGenerator.nextId();
        String deviceHash = tokenService.hash(session.deviceId());
        insert(new AccountSecurityEvent(
                eventId, userId, session.id(), eventType,
                AccountSecurityRiskLevel.INFO, session.clientType(), session.deviceName(),
                deviceHash, "EVENT:" + eventId, AccountSecurityEventStatus.UNREAD,
                occurredAt, null, null, null));
    }

    private void insert(AccountSecurityEvent event) {
        try {
            eventMapper.insertIfAbsent(event);
        } catch (DuplicateKeyException ignored) {
            // 并发首次登录由唯一范围键收敛为一条安全事件。
        }
    }

    private AccountSecurityEventView toView(AccountSecurityEvent event) {
        return new AccountSecurityEventView(
                event.id(), event.eventType(), event.riskLevel(), event.clientType(),
                event.deviceName(), event.status(), event.occurredAt(), event.readAt());
    }
}
