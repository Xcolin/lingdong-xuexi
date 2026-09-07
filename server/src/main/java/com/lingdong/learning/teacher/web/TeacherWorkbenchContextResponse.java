package com.lingdong.learning.teacher.web;

import com.lingdong.learning.teacher.application.TeacherClassSummary;
import com.lingdong.learning.teacher.application.TeacherWorkbenchContext;

import java.util.List;

/** 教师小程序工作台上下文响应。 */
public record TeacherWorkbenchContextResponse(
        String userId,
        String username,
        String displayName,
        List<String> permissionCodes,
        List<TeacherClassResponse> classes
) {
    static TeacherWorkbenchContextResponse from(TeacherWorkbenchContext context) {
        return new TeacherWorkbenchContextResponse(
                context.userId().toString(), context.username(), context.displayName(),
                context.permissionCodes(), context.classes().stream()
                .map(TeacherClassResponse::from).toList());
    }

    public record TeacherClassResponse(
            String classId,
            String className,
            String schoolId,
            String schoolName
    ) {
        static TeacherClassResponse from(TeacherClassSummary summary) {
            return new TeacherClassResponse(
                    summary.classId().toString(), summary.className(),
                    summary.schoolId().toString(), summary.schoolName());
        }
    }
}
