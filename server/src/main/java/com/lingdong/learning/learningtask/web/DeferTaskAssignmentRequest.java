package com.lingdong.learning.learningtask.web;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 管理角色手动顺延任务请求。 */
public record DeferTaskAssignmentRequest(@NotNull LocalDate targetDate) {
}
