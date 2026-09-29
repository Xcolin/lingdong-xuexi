package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.StudentWechatVerificationTicket;
import com.lingdong.learning.auth.application.StudentWechatVerificationTicketStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** 测试环境学生微信最终校验一次性票据存储，不连接远程 Redis。 */
@Component
@Profile("test")
public class InMemoryStudentWechatVerificationTicketStore implements StudentWechatVerificationTicketStore {
    private final Clock clock;
    private final ConcurrentHashMap<String, StoredTicket> tickets = new ConcurrentHashMap<>();

    public InMemoryStudentWechatVerificationTicketStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String ticketDigest, StudentWechatVerificationTicket ticket, Duration ttl) {
        tickets.put(ticketDigest, new StoredTicket(ticket, clock.instant().plus(ttl)));
    }

    @Override
    public StudentWechatVerificationTicket consume(String ticketDigest) {
        StoredTicket stored = tickets.remove(ticketDigest);
        return stored == null || !stored.expiresAt().isAfter(clock.instant()) ? null : stored.ticket();
    }

    private record StoredTicket(StudentWechatVerificationTicket ticket, Instant expiresAt) {
    }
}
