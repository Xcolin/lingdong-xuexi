package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建学生档案的 Web 请求，归属由是否提供机构标识决定。 */
public record CreateStudentRequest(
        @NotBlank @Size(max = 64) String studentName,
        @Size(max = 64) String gradeCode,
        Long organizationId
) {
}
