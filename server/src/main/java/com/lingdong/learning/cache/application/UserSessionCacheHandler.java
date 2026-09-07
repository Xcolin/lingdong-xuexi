package com.lingdong.learning.cache.application;

import com.lingdong.learning.auth.infrastructure.persistence.DeviceSessionMapper;
import com.lingdong.learning.cache.domain.CacheDomain;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 将用户会话缓存清除落实为撤销全部活动设备会话。 */
@Component
public class UserSessionCacheHandler implements ManagedCacheHandler {
    private final DeviceSessionMapper deviceSessionMapper;

    public UserSessionCacheHandler(DeviceSessionMapper deviceSessionMapper) {
        this.deviceSessionMapper = deviceSessionMapper;
    }

    @Override
    public CacheDomain domain() {
        return CacheDomain.USER_SESSION;
    }

    @Override
    public void clear() {
        deviceSessionMapper.revokeAllActiveSessions(LocalDateTime.now());
    }

    @Override
    public void refresh() {
        throw new IllegalStateException("用户会话只支持清除，不支持刷新");
    }
}
