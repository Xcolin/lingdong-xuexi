package com.lingdong.learning.auth.application;

/** 微信绑定票据不存在、过期或已消费时使用统一中性错误。 */
public class ParentWechatBindingTicketInvalidException extends RuntimeException {
    public ParentWechatBindingTicketInvalidException() {
        super("微信绑定凭证无效或已过期，请重新授权");
    }
}
