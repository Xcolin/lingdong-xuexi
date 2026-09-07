package com.lingdong.learning.auth.application;

/** 微信第三方调用前的接口服务登记与配置准入边界。 */
public interface ParentWechatIntegrationAccess {
    void requireAvailable();
}
