package com.lingdong.learning.student.application;

/** 机构管理员提交学生账号注销时的确认事实。 */
public record StudentAccountCancellationCommand(
        Long studentId,
        String reason,
        String confirmation
) {
}

