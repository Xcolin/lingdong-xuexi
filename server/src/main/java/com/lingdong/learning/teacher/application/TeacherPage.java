package com.lingdong.learning.teacher.application;

import java.util.List;

/** 教师目录分页结果。 */
public record TeacherPage(List<TeacherAccount> items, int page, int pageSize, long total) {
    public TeacherPage {
        items = List.copyOf(items);
    }
}
