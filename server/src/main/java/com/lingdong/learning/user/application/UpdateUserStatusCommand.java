package com.lingdong.learning.user.application;

import com.lingdong.learning.user.domain.UserStatus;

/** 系统管理端发起的账号状态调整请求。 */
public record UpdateUserStatusCommand(Long userId, UserStatus status) { }
