package com.lingdong.learning.student.domain;

import com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeDigest;

import java.time.LocalDateTime;

/** 学生登录码的不可逆凭证状态，不包含任何明文秘密。 */
public record StudentCredential(
        Long id,
        Long studentUserId,
        String codeHash,
        String codeSalt,
        String keyVersion,
        Integer failureCount,
        Boolean captchaRequired,
        LocalDateTime lockedUntil,
        LocalDateTime codeUpdatedAt,
        LocalDateTime lastSuccessAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StudentCredential initial(Long id, Long studentUserId, StudentLoginCodeDigest digest) {
        return new StudentCredential(
                id, studentUserId, digest.hash(), digest.salt(), digest.keyVersion(),
                0, false, null, null, null, null, null
        );
    }
}
