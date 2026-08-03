package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 设置平台账号密码的 HTTP 请求。 */
public record SetUserPasswordRequest(@NotBlank @Size(max = 64) String password) { }
