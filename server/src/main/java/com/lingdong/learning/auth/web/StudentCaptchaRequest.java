package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学生图形验证码申请，只绑定账号和当前设备。 */
public record StudentCaptchaRequest(
        @NotBlank @Size(max = 64) String studentAccount,
        @NotBlank @Size(max = 128) String deviceId
) {
}
