package com.lingdong.learning.auth.application;

/** 平台账号的 Web 密码登录请求。 */
public record PasswordLoginCommand(String username, String password, String deviceId, String deviceName) { }
