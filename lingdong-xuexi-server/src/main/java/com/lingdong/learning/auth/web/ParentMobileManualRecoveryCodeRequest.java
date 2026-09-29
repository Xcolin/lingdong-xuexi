package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 机构管理员向目标新手机号发送人工换绑验证码的请求。 */
public record ParentMobileManualRecoveryCodeRequest(
        @NotNull Long studentId,
        @NotNull Long parentUserId,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String newMobile
) {
}
