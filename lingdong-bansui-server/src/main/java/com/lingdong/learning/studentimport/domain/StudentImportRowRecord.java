package com.lingdong.learning.studentimport.domain;

import java.time.LocalDateTime;

/** 不保存源业务值，只保存处理结果和短期加密凭证的逐行事实。 */
public record StudentImportRowRecord(
        Long id,
        Long executionId,
        Integer rowNumber,
        StudentImportRowStatus status,
        Long studentId,
        String studentAccount,
        String failureCode,
        String failureMessage,
        byte[] credentialCiphertext,
        byte[] credentialNonce,
        String credentialKeyVersion,
        Integer attemptCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
