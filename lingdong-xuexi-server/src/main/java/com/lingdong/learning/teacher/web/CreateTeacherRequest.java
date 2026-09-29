package com.lingdong.learning.teacher.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 创建教师账号请求。 */
public record CreateTeacherRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 20) String displayName,
        @Size(max = 32) String mobile,
        @NotBlank @Size(min = 8, max = 20) String initialPassword,
        @NotNull Long schoolId,
        @Size(max = 100) List<@NotNull Long> classOrganizationIds
) {
}
