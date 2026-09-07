package com.lingdong.learning.student.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 学生首次分班或校内转班请求。 */
public record TransferStudentClassRequest(
        @NotNull Long classOrganizationId,
        @NotBlank @Size(max = 200) String reason
) {
}
