package com.lingdong.learning.student.infrastructure.persistence;

/** 学生注销候选查询行，学生用户标识只在服务端事务复核中使用。 */
public record StudentAccountCancellationCandidateRow(
        Long studentId,
        String studentName,
        Long studentUserId,
        String studentAccount,
        Long organizationId,
        String organizationName
) {
}

