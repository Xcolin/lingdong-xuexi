package com.lingdong.learning.auth.application;

/** 将微信临时登录凭证换取为服务端身份，业务层不感知供应商 HTTP 报文。 */
public interface ParentWechatIdentityGateway {
    ParentWechatIdentity exchange(String temporaryCode);
}
