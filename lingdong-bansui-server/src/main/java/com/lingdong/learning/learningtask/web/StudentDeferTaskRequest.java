package com.lingdong.learning.learningtask.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 学生申请任务顺延请求（05-09）：目标日期必填，申请原因选填上限 200 字。 */
public record StudentDeferTaskRequest(@NotNull LocalDate targetDate, @Size(max = 200) String reason) {
}
