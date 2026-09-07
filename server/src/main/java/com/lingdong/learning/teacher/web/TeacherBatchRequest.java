package com.lingdong.learning.teacher.web;

import com.lingdong.learning.teacher.application.TeacherBatchOperation;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Web 教师批量操作请求。 */
public record TeacherBatchRequest(
        @NotNull TeacherBatchOperation operation,
        @NotEmpty @Size(max = 100) List<@NotNull Long> teacherUserIds,
        Long classOrganizationId
) {
}
