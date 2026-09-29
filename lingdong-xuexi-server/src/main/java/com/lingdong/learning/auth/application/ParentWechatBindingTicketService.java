package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentWechatProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.springframework.stereotype.Service;

/** 签发并单次消费不透明微信绑定票据，客户端永远不能提交 openid 参与绑定。 */
@Service
public class ParentWechatBindingTicketService {
    private final ParentWechatBindingTicketStore store;
    private final SessionTokenService tokenService;
    private final ParentWechatProperties properties;

    public ParentWechatBindingTicketService(
            ParentWechatBindingTicketStore store,
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
            throw new ParentWechatBindingTicketInvalidException();
        }
        return identity;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
