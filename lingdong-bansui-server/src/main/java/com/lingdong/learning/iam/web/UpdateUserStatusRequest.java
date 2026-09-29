package com.lingdong.learning.iam.web;

import com.lingdong.learning.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

/** 用户状态变更接口请求。 */
public record UpdateUserStatusRequest(@NotNull UserStatus status) { }
