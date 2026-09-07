package com.lingdong.learning.learningtask.application;

/** 管理者任务进度的数据库分页参数。 */
public record ManagedTaskProgressQuery(
        Long taskId,
        Long teacherUserId,
        int limit,
        int offset
) {
}
