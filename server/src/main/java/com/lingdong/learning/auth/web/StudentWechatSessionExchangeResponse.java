package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.StudentWechatSessionExchange;

/** 学生微信交换响应，会话与绑定票据互斥。 */
public record StudentWechatSessionExchangeResponse(
        boolean bindingRequired,
        String bindingTicket,
        StudentWechatAuthenticatedSessionResponse session
) {
    static StudentWechatSessionExchangeResponse from(StudentWechatSessionExchange exchange) {
        return new StudentWechatSessionExchangeResponse(
                exchange.bindingRequired(), exchange.bindingTicket(),
                exchange.session() == null ? null : StudentWechatAuthenticatedSessionResponse.from(exchange.session()));
    }
}
