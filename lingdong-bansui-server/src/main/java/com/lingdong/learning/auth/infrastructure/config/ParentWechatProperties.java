package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 家长微信授权的一次性票据与调用超时配置。 */
@ConfigurationProperties(prefix = "lingdong.auth.parent-wechat")
public class ParentWechatProperties {
    private Duration bindingTicketTtl = Duration.ofMinutes(5);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(5);

    public Duration getBindingTicketTtl() {
        return bindingTicketTtl;
    }

    public void setBindingTicketTtl(Duration bindingTicketTtl) {
        this.bindingTicketTtl = bindingTicketTtl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
