package com.lingdong.learning.teacher.application;

/** 教师仍承担待审核任务时阻止状态或班级范围变更。 */
public class TeacherPendingReviewException extends IllegalStateException {
    public TeacherPendingReviewException() {
        super("教师仍有待审核任务，请先完成或转交审核");
    }
}
