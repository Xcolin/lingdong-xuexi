package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.DeviceSessionStatus;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 独立提交认证过程中发现的会话撤销，避免外层认证失败回滚安全状态。 */
@Service
public class DeviceSessionRevocationService {
    private final DeviceSessionMapper sessionMapper;

    public DeviceSessionRevocationService(DeviceSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeIfActive(Long sessionId, LocalDateTime revokedAt) {
        sessionMapper.updateStatusIfActive(sessionId, DeviceSessionStatus.REVOKED, revokedAt);
    }
}
