package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 学生本人任务分页结果。 */
public record StudentTaskAssignmentPage(
        List<StudentTaskAssignmentView> items, int page, int pageSize, long total
) {
    public StudentTaskAssignmentPage {
        items = List.copyOf(items);
    }
}
