package com.lingdong.learning.exportjob.application;

import java.util.List;

/** 本人导出作业分页结果。 */
public record ExportJobPage(List<ExportJobView> items, int page, int pageSize, long total) { }
