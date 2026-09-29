package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentWechatProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.springframework.stereotype.Service;

/** 签发并单次消费学生微信身份票据，不向客户端暴露微信身份。 */
@Service
public class StudentWechatIdentityTicketService {
    private final StudentWechatIdentityTicketStore store;
    private final SessionTokenService tokenService;
    private final ParentWechatProperties properties;

    public StudentWechatIdentityTicketService(
            StudentWechatIdentityTicketStore store,
            SessionTokenService tokenService,
            ParentWechatProperties properties
    ) {
        this.store = store;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public String issue(ParentWechatIdentity identity) {
        if (identity == null || isBlank(identity.appId()) || isBlank(identity.openId())) {
            throw new IllegalArgumentException("微信身份不完整");
        }
        String ticket = tokenService.newToken();
        store.save(tokenService.hash(ticket), identity.withoutSessionKey(), properties.getBindingTicketTtl());
        return ticket;
    }

    public ParentWechatIdentity consume(String ticket) {
        ParentWechatIdentity identity = store.consume(tokenService.hash(ticket));
        if (identity == null) {
            throw new StudentWechatTicketInvalidException();
        }
        return identity;
    }

    public ParentWechatIdentity find(String ticket) {
        ParentWechatIdentity identity = store.find(tokenService.hash(ticket));
        if (identity == null) {
            throw new StudentWechatTicketInvalidException();
        }
        return identity;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
