package com.lingdong.learning.learningtask.application;

/** Web 管理角色可顺延任务实例的受控分页查询。 */
public record ManagedDeferCandidateQuery(
        Long currentUserId,
        boolean parent,
        boolean teacher,
        boolean organizationAdministrator,
        int limit,
        long offset
) {
}
