package com.lingdong.learning.importjob.application;

import java.util.List;

/** 导入校验错误行分页结果。 */
public record ImportJobRowResultPage(
        List<ImportJobRowErrorView> items,
        int page,
        int pageSize,
        long total
) { }
