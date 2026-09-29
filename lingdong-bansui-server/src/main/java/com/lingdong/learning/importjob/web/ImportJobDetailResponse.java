package com.lingdong.learning.importjob.web;

import com.lingdong.learning.importjob.application.ImportJobDetailView;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;

import java.util.List;

/** 导入校验作业详情及创建时字段快照。 */
public record ImportJobDetailResponse(
        ImportJobResponse job,
        List<ImportFieldMappingSnapshot> fields
) {
    public static ImportJobDetailResponse from(ImportJobDetailView detail) {
        return new ImportJobDetailResponse(ImportJobResponse.from(detail.job()), detail.fields());
    }
}
