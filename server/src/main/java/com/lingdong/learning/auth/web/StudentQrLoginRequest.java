package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 小程序扫码登录请求，二维码票据与登录码均不写入日志。 */
public record StudentQrLoginRequest(
        @NotBlank @Size(max = 200) String qrContent,
        @NotBlank @Size(max = 16) String loginCode,
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Size(max = 100) String deviceName,
        @Size(max = 128) String captchaChallengeId,
        @Size(max = 16) String captchaAnswer
) {
}
