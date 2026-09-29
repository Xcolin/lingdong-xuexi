package com.lingdong.learning.teacher.application;

import java.util.List;

/** 教师批量操作请求。 */
public record TeacherBatchCommand(
        TeacherBatchOperation operation,
        List<Long> teacherUserIds,
        Long classOrganizationId
) {
}
