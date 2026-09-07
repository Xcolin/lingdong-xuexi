package com.lingdong.learning.auth.application;

import java.time.Duration;

/** 保存学生微信身份交换的一次性票据，调用方只能使用票据摘要。 */
public interface StudentWechatIdentityTicketStore {
    void save(String ticketDigest, ParentWechatIdentity identity, Duration ttl);

    ParentWechatIdentity find(String ticketDigest);

    ParentWechatIdentity consume(String ticketDigest);
}
