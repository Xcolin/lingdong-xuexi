package com.lingdong.learning.auth.infrastructure.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 微信小程序服务端身份凭证，只能由后端环境配置注入。 */
@ConfigurationProperties(prefix = "wx")
public class WechatMiniappProperties {
    private String appId = "";
    private String appSecret = "";

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean isComplete() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
