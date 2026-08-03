package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 任务表单可选择的教师及其活动授权班级。 */
public record TeacherOption(Long userId, String displayName, List<Long> classOrganizationIds) {
    public TeacherOption {
        classOrganizationIds = List.copyOf(classOrganizationIds);
    }
}
