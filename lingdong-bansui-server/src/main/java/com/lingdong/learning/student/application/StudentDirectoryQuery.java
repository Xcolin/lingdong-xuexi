package com.lingdong.learning.student.application;

/** 已完成角色识别与分页校验的学生目录查询条件。 */
public record StudentDirectoryQuery(
        String keyword,
        Long currentUserId,
        boolean systemAdministrator,
        boolean parent,
        boolean organizationAdministrator,
        int offset,
        int limit
) {
}
