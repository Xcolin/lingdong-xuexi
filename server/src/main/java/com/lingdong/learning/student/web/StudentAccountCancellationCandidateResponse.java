package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.StudentAccountCancellationCandidate;

/** 学生注销候选响应，雪花标识统一以字符串传输。 */
public record StudentAccountCancellationCandidateResponse(
        String studentId,
        String studentName,
        String studentAccount,
        String organizationId,
        String organizationName
) {
    public static StudentAccountCancellationCandidateResponse from(
            StudentAccountCancellationCandidate candidate
    ) {
        return new StudentAccountCancellationCandidateResponse(
                candidate.studentId().toString(), candidate.studentName(),
                candidate.studentAccount(), candidate.organizationId().toString(),
                candidate.organizationName());
    }
}
