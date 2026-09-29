package com.lingdong.learning.teacher.application;

import java.util.List;

/** 机构管理员创建教师账号时提交的最小业务字段。 */
public record CreateTeacherCommand(
        String username,
        String displayName,
        String mobile,
        String initialPassword,
        Long schoolId,
        List<Long> classOrganizationIds
) {
    public CreateTeacherCommand {
        classOrganizationIds = classOrganizationIds == null ? List.of() : List.copyOf(classOrganizationIds);
    }
}
