package com.lingdong.learning.teacher.application;

import com.lingdong.learning.user.domain.UserStatus;

/** 教师目录的组织范围分页条件。 */
public record TeacherQuery(
        String keyword,
        Long schoolId,
        Long classOrganizationId,
        UserStatus status,
        int page,
        int pageSize
) {
}
