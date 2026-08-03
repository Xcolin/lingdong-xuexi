package com.lingdong.learning.iam.web;

import com.lingdong.learning.user.domain.UserType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建用户的 HTTP 请求，仅包含用户自身基础资料。 */
public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String displayName,
        @Size(max = 32) String mobile,
        @NotNull UserType type
) { }
