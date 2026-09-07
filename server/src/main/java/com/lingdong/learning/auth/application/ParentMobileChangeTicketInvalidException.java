package com.lingdong.learning.auth.application;

/** 手机号变更验证票据缺失、过期、已消费或与当前上下文不一致。 */
public class ParentMobileChangeTicketInvalidException extends RuntimeException {
    public ParentMobileChangeTicketInvalidException() {
        super("手机号变更验证已失效，请重新验证当前手机号");
    }
}
