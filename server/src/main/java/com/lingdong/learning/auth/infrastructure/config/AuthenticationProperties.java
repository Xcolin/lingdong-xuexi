package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 认证令牌期限配置，生产环境通过受控环境变量覆盖。 */
@ConfigurationProperties(prefix = "lingdong.auth")
public class AuthenticationProperties {
    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(7);

    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
}
