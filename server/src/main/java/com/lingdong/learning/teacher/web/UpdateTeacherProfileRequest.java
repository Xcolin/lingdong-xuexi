package com.lingdong.learning.teacher.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 修改教师可变资料请求。 */
public record UpdateTeacherProfileRequest(
        @NotBlank @Size(max = 20) String displayName,
        @Size(max = 32) String mobile,
        boolean clearMobile
) {
}
