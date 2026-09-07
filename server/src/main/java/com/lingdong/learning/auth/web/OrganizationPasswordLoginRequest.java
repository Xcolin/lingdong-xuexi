package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 机构管理员小程序密码登录请求。 */
public record OrganizationPasswordLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String password,
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Size(max = 100) String deviceName
) { }
