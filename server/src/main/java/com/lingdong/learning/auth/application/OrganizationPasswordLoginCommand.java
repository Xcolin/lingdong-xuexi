package com.lingdong.learning.auth.application;

/** 机构管理员小程序账号密码登录命令。 */
public record OrganizationPasswordLoginCommand(
        String username,
        String password,
        String deviceId,
        String deviceName
) { }
