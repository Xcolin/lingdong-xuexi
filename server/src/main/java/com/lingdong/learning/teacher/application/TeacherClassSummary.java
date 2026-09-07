package com.lingdong.learning.teacher.application;

/** 教师工作台使用的活动班级摘要。 */
public record TeacherClassSummary(
        Long classId,
        String className,
        Long schoolId,
        String schoolName
) {
}
