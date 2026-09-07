package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentWechatBindingTicketStore;
import com.lingdong.learning.auth.application.ParentWechatIdentity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境一次性微信绑定票据存储，不连接远程 Redis。 */
@Component
@Profile("test")
public class InMemoryParentWechatBindingTicketStore implements ParentWechatBindingTicketStore {
    private final Clock clock;
    private final ConcurrentHashMap<String, StoredIdentity> tickets = new ConcurrentHashMap<>();

    public InMemoryParentWechatBindingTicketStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String ticketDigest, ParentWechatIdentity identity, Duration ttl) {
        tickets.put(ticketDigest, new StoredIdentity(identity, clock.instant().plus(ttl)));
    }

    @Override
    public ParentWechatIdentity consume(String ticketDigest) {
        StoredIdentity stored = tickets.remove(ticketDigest);
        if (stored == null || !stored.expiresAt().isAfter(clock.instant())) {
            return null;
        }
        return stored.identity();
    }

    private record StoredIdentity(ParentWechatIdentity identity, Instant expiresAt) {
    }
}
