package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;

import java.time.LocalDateTime;

/** 不包含姓名、登录码和密文的学员导入逐行结果。 */
public record StudentImportRowView(
        Long id,
        int rowNumber,
        StudentImportRowStatus status,
        Long studentId,
        String studentAccount,
        String failureCode,
        String failureMessage,
        int attemptCount,
        LocalDateTime updatedAt
) {
    public static StudentImportRowView from(StudentImportRowRecord row) {
        return new StudentImportRowView(
                row.id(), row.rowNumber(), row.status(), row.studentId(), row.studentAccount(),
                row.failureCode(), row.failureMessage(), row.attemptCount(), row.updatedAt());
    }
}
