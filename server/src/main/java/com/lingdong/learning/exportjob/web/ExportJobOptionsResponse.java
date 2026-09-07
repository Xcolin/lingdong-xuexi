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
        boolean sensitive
) {
    static ExportJobOptionsResponse from(ExportJobOptions options) {
        return new ExportJobOptionsResponse(
                options.exportType(), options.templateName(), options.templateVersion(),
                options.columns(), options.students().stream()
                .map(student -> new StudentOptionResponse(
                        student.studentId().toString(), student.studentName())).toList(),
                options.sensitive());
    }

    public record StudentOptionResponse(String id, String name) { }
}
