package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.StudentTaskAssignmentPage;

import java.util.List;

/** 小程序端学生本人任务分页响应。 */
public record StudentTaskAssignmentPageResponse(
        List<StudentTaskAssignmentResponse> items, int page, int pageSize, long total
) {
    static StudentTaskAssignmentPageResponse from(StudentTaskAssignmentPage page) {
        return new StudentTaskAssignmentPageResponse(
                page.items().stream().map(StudentTaskAssignmentResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
