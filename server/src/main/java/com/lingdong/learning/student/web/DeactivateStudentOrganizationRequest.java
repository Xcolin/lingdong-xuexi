package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 学生离校或跨机构转学转出请求。 */
public record DeactivateStudentOrganizationRequest(
        @NotNull Long organizationId,
        @NotBlank @Size(max = 200) String reason
) {
}
