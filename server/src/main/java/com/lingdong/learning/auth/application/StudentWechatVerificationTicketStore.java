package com.lingdong.learning.auth.application;

import java.time.Duration;

/** 保存学生微信最终绑定校验的一次性服务端上下文。 */
public interface StudentWechatVerificationTicketStore {
    void save(String ticketDigest, StudentWechatVerificationTicket ticket, Duration ttl);

    StudentWechatVerificationTicket consume(String ticketDigest);
}
