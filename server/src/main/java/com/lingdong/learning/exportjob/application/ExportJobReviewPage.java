package com.lingdong.learning.exportjob.application;

import java.util.List;

/** 待审核敏感导出分页结果。 */
public record ExportJobReviewPage(List<ExportJobReviewView> items, int page, int pageSize, long total) { }
