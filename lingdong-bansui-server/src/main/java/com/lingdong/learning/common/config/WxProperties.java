package com.lingdong.learning.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wx")
public record WxProperties(String appId, String appSecret) {
}
