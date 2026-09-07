package com.lingdong.learning.auth.application;

import java.time.Duration;

/** 保存手机号变更验证票据，持久层只能接收不可逆票据摘要。 */
public interface ParentMobileChangeTicketStore {
    void save(String ticketDigest, ParentMobileChangeTicket ticket, Duration ttl);

    ParentMobileChangeTicket find(String ticketDigest);

    ParentMobileChangeTicket consume(String ticketDigest);
}
