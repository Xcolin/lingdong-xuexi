package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.DeviceSessionStatus;
import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 认证过程中发现的会话撤销；无外层事务时独立提交，有外层事务时加入以避免同行锁自锁挂起。 */
@Service
public class DeviceSessionRevocationService {
    private final DeviceSessionMapper sessionMapper;

    public DeviceSessionRevocationService(DeviceSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    // 不能用 REQUIRES_NEW：外层事务已持有 auth_device_session 行锁时挂起外层再更新同一行会自锁等待（InnoDB 50s 超时）；
    // 主调用链（SessionAuthenticationFilter 认证）本身无外层事务，此处新事务语义不变。
    @Transactional
    public void revokeIfActive(Long sessionId, LocalDateTime revokedAt) {
        sessionMapper.updateStatusIfActive(sessionId, DeviceSessionStatus.REVOKED, revokedAt);
    }
}
