package com.lingdong.learning.auth.application;

/** 系统管理员设置平台账号密码的请求。 */
public record SetPlatformUserPasswordCommand(Long operatorId, Long userId, String password) { }
