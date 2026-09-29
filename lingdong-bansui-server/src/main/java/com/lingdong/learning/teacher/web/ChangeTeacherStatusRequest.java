package com.lingdong.learning.teacher.web;

import com.lingdong.learning.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

/** 教师账号状态变更请求。 */
public record ChangeTeacherStatusRequest(@NotNull UserStatus status) {
}
