package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;

import java.util.List;

/** 隔离具体业务查询与通用导出作业编排。 */
public interface ExportDatasetAdapter {
    ExportJobType type();

    boolean sensitive();

    List<ExportColumnDefinition> columns();

    long captureUpperBound(ExportRequestDefinition request);

    long count(ExportRequestDefinition request, long upperBound);

    ExportDataPage fetchAfter(ExportRequestDefinition request, long upperBound, long cursor, int limit);
}
