package com.lingdong.learning.teacher.web;

import com.lingdong.learning.teacher.application.TeacherPage;

import java.util.List;

/** 教师目录分页响应。 */
public record TeacherPageResponse(
        List<TeacherResponse> items,
        int page,
        int pageSize,
        long total
) {
    static TeacherPageResponse from(TeacherPage page) {
        return new TeacherPageResponse(
                page.items().stream().map(TeacherResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
