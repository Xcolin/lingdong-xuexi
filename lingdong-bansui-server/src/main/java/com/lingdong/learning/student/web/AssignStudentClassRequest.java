package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 设置学生当前班级的 Web 请求。 */
public record AssignStudentClassRequest(@NotNull @Positive Long classOrganizationId) {
}
