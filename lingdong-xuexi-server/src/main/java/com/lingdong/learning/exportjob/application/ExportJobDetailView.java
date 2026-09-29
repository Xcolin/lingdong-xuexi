package com.lingdong.learning.exportjob.application;

import java.util.List;

/** 作业详情仅提供受控列和对象范围摘要。 */
public record ExportJobDetailView(
        ExportJobView job,
        List<ExportColumnSnapshot> columns,
        String scopeSummary,
        List<ExportJobEventView> events
) { }
