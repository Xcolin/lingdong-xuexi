package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.StudentDirectoryPage;

import java.util.List;

/** 学生目录分页响应。 */
public record StudentDirectoryPageResponse(List<StudentResponse> items, int page, int pageSize, long total) {
    static StudentDirectoryPageResponse from(StudentDirectoryPage page) {
        return new StudentDirectoryPageResponse(
                page.items().stream().map(StudentResponse::from).toList(), page.page(), page.pageSize(), page.total()
        );
    }
}
