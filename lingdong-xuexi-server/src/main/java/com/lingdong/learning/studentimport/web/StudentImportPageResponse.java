package com.lingdong.learning.studentimport.web;

import com.lingdong.learning.studentimport.application.StudentImportPage;

import java.util.List;

/** 学员导入执行分页响应。 */
public record StudentImportPageResponse(
        List<StudentImportResponse> items,
        int page,
        int pageSize,
        long total
) {
    static StudentImportPageResponse from(StudentImportPage page) {
        return new StudentImportPageResponse(
                page.items().stream().map(StudentImportResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
