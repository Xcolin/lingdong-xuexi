package com.lingdong.learning.teacher.application;

import java.util.List;

/** 当前教师小程序会话的服务端可信工作台上下文。 */
public record TeacherWorkbenchContext(
        Long userId,
        String username,
        String displayName,
        List<String> permissionCodes,
        List<TeacherClassSummary> classes
) {
}
