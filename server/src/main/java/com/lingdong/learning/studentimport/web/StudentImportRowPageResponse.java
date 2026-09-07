package com.lingdong.learning.studentimport.web;

import com.lingdong.learning.studentimport.application.StudentImportRowPage;

import java.util.List;

/** 学员导入逐行结果分页响应。 */
public record StudentImportRowPageResponse(
        List<StudentImportRowResponse> items,
        int page,
        int pageSize,
        long total
) {
    static StudentImportRowPageResponse from(StudentImportRowPage page) {
        return new StudentImportRowPageResponse(
                page.items().stream().map(StudentImportRowResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
