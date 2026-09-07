package com.lingdong.learning.importjob.application;

import java.util.List;

/** 导入校验作业分页结果。 */
public record ImportJobPage(List<ImportJobView> items, int page, int pageSize, long total) { }
