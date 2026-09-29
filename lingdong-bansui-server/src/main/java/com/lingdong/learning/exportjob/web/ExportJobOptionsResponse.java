package com.lingdong.learning.exportjob.web;

import com.lingdong.learning.exportjob.application.ExportJobOptions;
import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;

import java.util.List;

/** 导出创建选项响应。 */
public record ExportJobOptionsResponse(
        ExportJobType exportType,
        String templateName,
        String templateVersion,
        List<ExportColumnDefinition> columns,
        List<StudentOptionResponse> students,
        boolean sensitive,
        List<String> systemTaskTypes,
        List<StudentOptionResponse> exceptionClasses,
        List<StudentOptionResponse> orgStatClasses,
        List<StudentOptionResponse> attClasses
) {
    public ExportJobOptionsResponse(ExportJobType exportType, String templateName, String templateVersion, List<ExportColumnDefinition> columns, List<StudentOptionResponse> students, boolean sensitive, List<String> systemTaskTypes) {
        this(exportType, templateName, templateVersion, columns, students, sensitive, systemTaskTypes, List.of(), List.of(), List.of());
    }
    static ExportJobOptionsResponse from(ExportJobOptions options) {
        return new ExportJobOptionsResponse(
                options.exportType(), options.templateName(), options.templateVersion(),
                options.columns(), options.students().stream()
                .map(student -> new StudentOptionResponse(
                        student.studentId().toString(), student.studentName())).toList(),
                options.sensitive(), options.systemTaskTypes(), options.exceptionClasses().stream()
                .map(cl -> new StudentOptionResponse(cl.classOrganizationId().toString(), cl.className())).toList(),
                options.orgStatClasses().stream()
                .map(cl -> new StudentOptionResponse(cl.classOrganizationId().toString(), cl.className())).toList(),
                options.attClasses().stream()
                .map(cl -> new StudentOptionResponse(cl.classOrganizationId().toString(), cl.className())).toList());
    }

    public record StudentOptionResponse(String id, String name) { }
}
