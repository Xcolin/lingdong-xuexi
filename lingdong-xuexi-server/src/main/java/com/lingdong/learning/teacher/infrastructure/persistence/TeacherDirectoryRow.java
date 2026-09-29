package com.lingdong.learning.teacher.infrastructure.persistence;

import com.lingdong.learning.user.domain.UserStatus;

import java.time.LocalDateTime;

/** 教师目录单行结果，不包含密码摘要。 */
public record TeacherDirectoryRow(
        Long id,
        String username,
        String displayName,
        String mobile,
        UserStatus status,
        Long schoolId,
        String schoolName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
