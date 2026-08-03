package com.lingdong.learning.auth.application;

/** 刷新当前设备会话凭证的请求。 */
public record RefreshSessionCommand(String refreshToken) { }
