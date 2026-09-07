package com.lingdong.learning.studentimport.infrastructure.persistence;

import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 学员导入逐行结果与短期加密凭证持久化。 */
@Mapper
public interface StudentImportRowMapper {
    int insertBatch(@Param("rows") List<StudentImportRowRecord> rows);

    List<StudentImportRowRecord> findByExecutionIdAndStatus(
            @Param("executionId") Long executionId,
            @Param("status") StudentImportRowStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByExecutionIdAndStatus(
            @Param("executionId") Long executionId,
            @Param("status") StudentImportRowStatus status
    );

    int markSucceeded(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("studentAccount") String studentAccount,
            @Param("credentialCiphertext") byte[] credentialCiphertext,
            @Param("credentialNonce") byte[] credentialNonce,
            @Param("credentialKeyVersion") String credentialKeyVersion
    );

    int markFailed(
            @Param("id") Long id,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage
    );

    int clearCredentials(@Param("executionId") Long executionId);
}
