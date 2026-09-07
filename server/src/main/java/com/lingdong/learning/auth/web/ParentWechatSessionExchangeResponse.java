package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lingdong.learning.auth.application.ParentWechatSessionExchange;

/** 微信授权交换响应，只返回家长会话或不透明绑定票据。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParentWechatSessionExchangeResponse(
        boolean bindingRequired,
        String bindingTicket,
        ParentSessionResponse session
) {
    public static ParentWechatSessionExchangeResponse from(ParentWechatSessionExchange exchange) {
        return new ParentWechatSessionExchangeResponse(
                exchange.bindingRequired(), exchange.bindingTicket(),
                exchange.session() == null ? null : ParentSessionResponse.from(exchange.session()));
    }
}
