package com.lingdong.learning.auth.application;

import java.time.Duration;

/** 保存微信手机号绑定的一次性票据，调用方只能使用票据摘要。 */
public interface ParentWechatBindingTicketStore {
    void save(String ticketDigest, ParentWechatIdentity identity, Duration ttl);

    ParentWechatIdentity consume(String ticketDigest);
}
