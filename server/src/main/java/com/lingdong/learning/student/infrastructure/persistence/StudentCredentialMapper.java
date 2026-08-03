package com.lingdong.learning.student.infrastructure.persistence;

import com.lingdong.learning.student.domain.StudentCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 学生登录凭证的持久化边界，不接受明文登录码。 */
@Mapper
public interface StudentCredentialMapper {
    StudentCredential findByStudentUserId(@Param("studentUserId") Long studentUserId);

    StudentCredential findByStudentUserIdForUpdate(@Param("studentUserId") Long studentUserId);

    int insert(@Param("credential") StudentCredential credential);

    int resetLoginCode(@Param("studentUserId") Long studentUserId, @Param("digest") com.lingdong.learning.auth.infrastructure.security.StudentLoginCodeDigest digest);

    int updateFailureState(
            @Param("studentUserId") Long studentUserId,
            @Param("failureCount") int failureCount,
            @Param("captchaRequired") boolean captchaRequired,
            @Param("lockedUntil") java.time.LocalDateTime lockedUntil
    );

    int markLoginSuccess(
            @Param("studentUserId") Long studentUserId,
            @Param("succeededAt") java.time.LocalDateTime succeededAt
    );
}
