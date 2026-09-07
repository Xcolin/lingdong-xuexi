package com.lingdong.learning.studentimport.application;

/** 从已通过校验的作业创建学员业务导入执行。 */
public record CreateStudentImportCommand(
        Long operatorId,
        Long validationJobId,
        Long classOrganizationId
) { }
