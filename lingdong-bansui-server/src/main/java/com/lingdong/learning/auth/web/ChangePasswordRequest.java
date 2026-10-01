package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前账号修改密码，不接收目标账号或会话身份。 */
public record ChangePasswordRequest(
        @NotBlank @Size(max = 64) String oldPassword,
        @NotBlank @Size(max = 20) String newPassword
) { }
