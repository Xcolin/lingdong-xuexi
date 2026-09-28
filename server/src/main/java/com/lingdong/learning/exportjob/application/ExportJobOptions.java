package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointStudentOptionRow;

import java.util.List;

/** 创建页面所需的模板、字段和可选学生。 */
public record ExportJobOptions(
        ExportJobType exportType,
        String templateName,
        String templateVersion,
        List<ExportColumnDefinition> columns,
        List<GrowthPointStudentOptionRow> students,
        boolean sensitive,
        List<String> systemTaskTypes,
        List<com.lingdong.learning.exceptionreport.application.ExceptionReportClassOption> exceptionClasses
) {
    public ExportJobOptions(ExportJobType exportType, String templateName, String templateVersion, List<ExportColumnDefinition> columns, List<GrowthPointStudentOptionRow> students, boolean sensitive, List<String> systemTaskTypes) {
        this(exportType, templateName, templateVersion, columns, students, sensitive, systemTaskTypes, List.of());
    }
    public ExportJobOptions(ExportJobType exportType, String templateName, String templateVersion,
            List<ExportColumnDefinition> columns, List<GrowthPointStudentOptionRow> students, boolean sensitive) {
        this(exportType, templateName, templateVersion, columns, students, sensitive, List.of());
    }
}
