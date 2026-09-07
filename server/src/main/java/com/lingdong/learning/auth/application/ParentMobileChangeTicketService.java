package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 签发、校验并单次消费与旧手机号验证上下文绑定的不透明票据。 */
@Service
public class ParentMobileChangeTicketService {
    private final ParentMobileChangeTicketStore store;
    private final SessionTokenService tokenService;
    private final ParentSmsProperties properties;

    public ParentMobileChangeTicketService(
            ParentMobileChangeTicketStore store,
            SessionTokenService tokenService,
            ParentSmsProperties properties
    ) {
        this.store = store;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public String issue(Long userId, AuthClientType clientType, String currentMobileDigest) {
        ParentMobileChangeTicket context = requiredContext(userId, clientType, currentMobileDigest);
        String ticket = tokenService.newToken();
        store.save(tokenService.hash(ticket), context, properties.getMobileChangeTicketTtl());
        return ticket;
    }

    public ParentMobileChangeTicket requireValid(
            String ticket,
            Long userId,
            AuthClientType clientType,
            String currentMobileDigest
    ) {
        return validate(store.find(digest(ticket)), userId, clientType, currentMobileDigest);
    }

    public ParentMobileChangeTicket consume(
            String ticket,
            Long userId,
            AuthClientType clientType,
            String currentMobileDigest
    ) {
        return validate(store.consume(digest(ticket)), userId, clientType, currentMobileDigest);
    }

    private ParentMobileChangeTicket validate(
            ParentMobileChangeTicket stored,
            Long userId,
            AuthClientType clientType,
            String currentMobileDigest
    ) {
        if (stored == null
                || !Objects.equals(stored.userId(), userId)
                || stored.clientType() != clientType
                || !Objects.equals(stored.currentMobileDigest(), currentMobileDigest)) {
            throw new ParentMobileChangeTicketInvalidException();
        }
        return stored;
    }

    private ParentMobileChangeTicket requiredContext(
            Long userId,
            AuthClientType clientType,
            String currentMobileDigest
    ) {
        if (userId == null || clientType == null || currentMobileDigest == null || currentMobileDigest.isBlank()) {
            throw new IllegalArgumentException("手机号变更验证上下文不完整");
        }
        return new ParentMobileChangeTicket(userId, clientType, currentMobileDigest);
    }

    private String digest(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw new ParentMobileChangeTicketInvalidException();
        }
        return tokenService.hash(ticket);
    }
}
