package com.lingdong.learning.auth.application;

/** 微信凭证交换结果：返回会话或一次性绑定票据，两者互斥。 */
public record ParentWechatSessionExchange(
        boolean bindingRequired,
        String bindingTicket,
        ParentPhoneAuthenticatedSession session
) {
    public static ParentWechatSessionExchange bindingRequired(String ticket) {
        return new ParentWechatSessionExchange(true, ticket, null);
    }

    public static ParentWechatSessionExchange authenticated(ParentPhoneAuthenticatedSession session) {
        return new ParentWechatSessionExchange(false, null, session);
    }
}
