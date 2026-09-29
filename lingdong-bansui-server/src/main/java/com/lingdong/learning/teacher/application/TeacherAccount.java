package com.lingdong.learning.teacher.application;

import com.lingdong.learning.user.domain.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 教师管理接口内部使用的脱密账号摘要。 */
public record TeacherAccount(
        Long id,
        String username,
        String displayName,
        String mobile,
        UserStatus status,
        Long schoolId,
        String schoolName,
        List<Long> classOrganizationIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public TeacherAccount {
        classOrganizationIds = List.copyOf(classOrganizationIds);
    }
}
