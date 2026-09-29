package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Web 平台账号密码登录的 HTTP 请求。 */
public record PasswordLoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String password,
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Size(max = 100) String deviceName
) { }
