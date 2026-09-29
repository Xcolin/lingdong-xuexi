package com.lingdong.learning.studentimport.web;

import com.lingdong.learning.studentimport.application.StudentImportRowView;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;

import java.time.LocalDateTime;

/** 不包含姓名、登录码和密文的逐行结果响应。 */
public record StudentImportRowResponse(
        String id,
        int rowNumber,
        StudentImportRowStatus status,
        String studentId,
        String studentAccount,
        String failureCode,
        String failureMessage,
        int attemptCount,
        LocalDateTime updatedAt
) {
    static StudentImportRowResponse from(StudentImportRowView row) {
        return new StudentImportRowResponse(
                row.id().toString(), row.rowNumber(), row.status(),
                row.studentId() == null ? null : row.studentId().toString(),
                row.studentAccount(), row.failureCode(), row.failureMessage(),
                row.attemptCount(), row.updatedAt());
    }
}
