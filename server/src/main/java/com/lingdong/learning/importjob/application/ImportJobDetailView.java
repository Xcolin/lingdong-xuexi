package com.lingdong.learning.importjob.application;

import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;

import java.util.List;

/** 导入校验作业详情及其不可变字段快照。 */
public record ImportJobDetailView(
        ImportJobView job,
        List<ImportFieldMappingSnapshot> fields
) { }
