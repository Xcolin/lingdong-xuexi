package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.config.ParentWechatProperties;
import com.lingdong.learning.auth.infrastructure.security.SessionTokenService;
import org.springframework.stereotype.Service;

/** 签发并单次消费学生微信最终绑定校验票据。 */
@Service
public class StudentWechatVerificationTicketService {
    private final StudentWechatVerificationTicketStore store;
    private final SessionTokenService tokenService;
    private final ParentWechatProperties properties;

    public StudentWechatVerificationTicketService(
            StudentWechatVerificationTicketStore store,
            SessionTokenService tokenService,
            ParentWechatProperties properties
    ) {
        this.store = store;
        this.tokenService = tokenService;
        this.properties = properties;
    }

    public String issue(StudentWechatVerificationTicket verification) {
        if (verification == null || verification.identity() == null
                || verification.studentId() == null || verification.studentUserId() == null
                || verification.primaryParentUserId() == null
                || verification.primaryParentMobile() == null || verification.primaryParentMobile().isBlank()
                || verification.deviceId() == null || verification.deviceId().isBlank()) {
            throw new IllegalArgumentException("学生微信绑定校验上下文不完整");
        }
        String ticket = tokenService.newToken();
        store.save(tokenService.hash(ticket), verification, properties.getBindingTicketTtl());
        return ticket;
    }

    public StudentWechatVerificationTicket consume(String ticket) {
        StudentWechatVerificationTicket verification = store.consume(tokenService.hash(ticket));
        if (verification == null) {
            throw new StudentWechatTicketInvalidException();
        }
        return verification;
    }
}
