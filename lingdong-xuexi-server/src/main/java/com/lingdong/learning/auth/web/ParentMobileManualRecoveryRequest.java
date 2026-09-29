package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 机构管理员完成线下核验后提交的家长手机号人工换绑请求。 */
public record ParentMobileManualRecoveryRequest(
        @NotNull Long studentId,
        @NotNull Long parentUserId,
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$") String newMobile,
        @NotBlank @Pattern(regexp = "^\\d{6}$") String smsCode,
        @NotBlank @Size(max = 200) String reason,
        @NotBlank String confirmation
) {
}
