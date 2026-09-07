package com.lingdong.learning.studentimport.web;

import com.lingdong.learning.studentimport.application.CreateStudentImportCommand;
import jakarta.validation.constraints.NotNull;

/** 创建学员导入执行请求。 */
public record CreateStudentImportRequest(
        @NotNull Long validationJobId,
        Long classOrganizationId
) {
    CreateStudentImportCommand toCommand(Long operatorId) {
        return new CreateStudentImportCommand(operatorId, validationJobId, classOrganizationId);
    }
}
