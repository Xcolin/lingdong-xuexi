package com.lingdong.learning.auth.application;

/** 学生微信交换结果，会话与首次绑定票据互斥。 */
public record StudentWechatSessionExchange(
        boolean bindingRequired,
        String bindingTicket,
        StudentWechatAuthenticatedSession session
) {
    public static StudentWechatSessionExchange bindingRequired(String ticket) {
        return new StudentWechatSessionExchange(true, ticket, null);
    }

    public static StudentWechatSessionExchange authenticated(StudentWechatAuthenticatedSession session) {
        return new StudentWechatSessionExchange(false, null, session);
    }
}
