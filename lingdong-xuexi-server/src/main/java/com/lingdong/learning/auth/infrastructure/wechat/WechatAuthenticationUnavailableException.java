package com.lingdong.learning.auth.infrastructure.wechat;

/** 微信配置、网络或凭证校验异常的受控错误，不回显第三方原始报文。 */
public class WechatAuthenticationUnavailableException extends RuntimeException {
    public WechatAuthenticationUnavailableException() {
        super("微信认证暂不可用，请使用手机号登录");
    }
}
