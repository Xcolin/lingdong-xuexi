package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 机构管理员提交的学生账号注销确认信息。 */
public record StudentAccountCancellationRequest(
        @NotBlank @Size(max = 200) String reason,
        @NotBlank String confirmation
) {
}
