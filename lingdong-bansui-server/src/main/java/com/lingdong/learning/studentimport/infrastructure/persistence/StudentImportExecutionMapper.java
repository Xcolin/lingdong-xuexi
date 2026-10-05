package com.lingdong.learning.studentimport.infrastructure.persistence;

import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 学员导入执行持久化与带版本条件的状态迁移。 */
@Mapper
public interface StudentImportExecutionMapper {
    int insert(@Param("execution") StudentImportExecutionRecord execution);

    StudentImportExecutionRecord findById(@Param("id") Long id);

    StudentImportExecutionRecord findByValidationJobId(@Param("validationJobId") Long validationJobId);

    List<StudentImportExecutionRecord> findQueued(@Param("limit") int limit);

    List<StudentImportExecutionRecord> findPageByRequester(
            @Param("requesterId") Long requesterId,
            @Param("status") StudentImportExecutionStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByRequester(
            @Param("requesterId") Long requesterId,
            @Param("status") StudentImportExecutionStatus status
    );

    List<StudentImportExecutionRecord> findPageByRequesterScope(
            @Param("requesterId") Long requesterId,
            @Param("status") StudentImportExecutionStatus status,
            @Param("scope") com.lingdong.learning.datascope.application.OrganizationDataScope scope,
            @Param("offset") int offset, @Param("limit") int limit);

    long countByRequesterScope(@Param("requesterId") Long requesterId,
            @Param("status") StudentImportExecutionStatus status,
            @Param("scope") com.lingdong.learning.datascope.application.OrganizationDataScope scope);

    int claim(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    int complete(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("status") StudentImportExecutionStatus status,
            @Param("processedRows") int processedRows,
            @Param("succeededRows") int succeededRows,
            @Param("failedRows") int failedRows,
            @Param("credentialFileId") Long credentialFileId,
            @Param("credentialExpiresAt") LocalDateTime credentialExpiresAt,
            @Param("completedAt") LocalDateTime completedAt
    );

    int fail(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("completedAt") LocalDateTime completedAt
    );

    int requeueFailures(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("queuedAt") LocalDateTime queuedAt
    );

    int consumeCredential(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("downloadedAt") LocalDateTime downloadedAt,
            @Param("now") LocalDateTime now
    );

    List<StudentImportExecutionRecord> findExpiredCredentials(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int expireCredential(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("now") LocalDateTime now
    );
}
