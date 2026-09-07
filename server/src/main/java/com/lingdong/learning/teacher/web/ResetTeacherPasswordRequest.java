package com.lingdong.learning.teacher.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 机构管理员重置教师密码请求。 */
public record ResetTeacherPasswordRequest(
        @NotBlank @Size(min = 8, max = 20) String newPassword
) {
}
