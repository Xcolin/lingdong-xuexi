package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentWechatIdentity;
import com.lingdong.learning.auth.application.StudentWechatIdentityTicketStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境学生微信身份一次性票据存储，不连接远程 Redis。 */
@Component
@Profile("test")
public class InMemoryStudentWechatIdentityTicketStore implements StudentWechatIdentityTicketStore {
    private final Clock clock;
    private final ConcurrentHashMap<String, StoredIdentity> tickets = new ConcurrentHashMap<>();

    public InMemoryStudentWechatIdentityTicketStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String ticketDigest, ParentWechatIdentity identity, Duration ttl) {
        tickets.put(ticketDigest, new StoredIdentity(identity, clock.instant().plus(ttl)));
    }

    @Override
    public ParentWechatIdentity find(String ticketDigest) {
        StoredIdentity stored = tickets.get(ticketDigest);
        if (stored == null || !stored.expiresAt().isAfter(clock.instant())) {
            if (stored != null) {
                tickets.remove(ticketDigest, stored);
            }
            return null;
        }
        return stored.identity();
    }

    @Override
    public ParentWechatIdentity consume(String ticketDigest) {
        StoredIdentity stored = tickets.remove(ticketDigest);
        return stored == null || !stored.expiresAt().isAfter(clock.instant()) ? null : stored.identity();
    }

    private record StoredIdentity(ParentWechatIdentity identity, Instant expiresAt) {
    }
}
