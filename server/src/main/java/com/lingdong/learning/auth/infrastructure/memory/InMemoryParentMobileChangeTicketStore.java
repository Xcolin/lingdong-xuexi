package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentMobileChangeTicket;
import com.lingdong.learning.auth.application.ParentMobileChangeTicketStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境手机号变更票据存储，不连接远程 Redis。 */
@Component
@Profile("test")
public class InMemoryParentMobileChangeTicketStore implements ParentMobileChangeTicketStore {
    private final Clock clock;
    private final ConcurrentHashMap<String, StoredTicket> tickets = new ConcurrentHashMap<>();

    public InMemoryParentMobileChangeTicketStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String ticketDigest, ParentMobileChangeTicket ticket, Duration ttl) {
        tickets.put(ticketDigest, new StoredTicket(ticket, clock.instant().plus(ttl)));
    }

    @Override
    public ParentMobileChangeTicket find(String ticketDigest) {
        StoredTicket stored = tickets.get(ticketDigest);
        if (stored == null || !stored.expiresAt().isAfter(clock.instant())) {
            tickets.remove(ticketDigest, stored);
            return null;
        }
        return stored.ticket();
    }

    @Override
    public ParentMobileChangeTicket consume(String ticketDigest) {
        StoredTicket stored = tickets.remove(ticketDigest);
        if (stored == null || !stored.expiresAt().isAfter(clock.instant())) {
            return null;
        }
        return stored.ticket();
    }

    private record StoredTicket(ParentMobileChangeTicket ticket, Instant expiresAt) {
    }
}
