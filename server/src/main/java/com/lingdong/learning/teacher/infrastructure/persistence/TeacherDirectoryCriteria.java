package com.lingdong.learning.teacher.infrastructure.persistence;

import com.lingdong.learning.user.domain.UserStatus;

import java.util.List;

/** 传递给 MyBatis 的已归一化教师目录条件。 */
public record TeacherDirectoryCriteria(
        String keyword,
        Long schoolId,
        Long classOrganizationId,
        UserStatus status,
        boolean allOrganizations,
        List<String> rootPaths,
        int limit,
        int offset
) {
}
