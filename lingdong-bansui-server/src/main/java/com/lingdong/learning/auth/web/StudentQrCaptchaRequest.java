package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 扫码登录在触发风控后使用新票据领取图形验证码。 */
public record StudentQrCaptchaRequest(
        @NotBlank @Size(max = 200) String qrContent,
        @NotBlank @Size(max = 128) String deviceId
) {
}
