package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 导出作业持久化和带版本条件的状态迁移。 */
@Mapper
public interface ExportJobMapper {
    List<ExportJobRecord> findReviewPageByRequesterAndStudent(
            @Param("requesterId") Long requesterId, @Param("studentId") Long studentId,
            @Param("status") ExportJobStatus status, @Param("offset") int offset, @Param("limit") int limit);

    long countReviewsByRequesterAndStudent(@Param("requesterId") Long requesterId,
            @Param("studentId") Long studentId, @Param("status") ExportJobStatus status);

    int insert(@Param("job") ExportJobRecord job);

    ExportJobRecord findById(@Param("id") Long id);

    ExportJobRecord findBySystemTaskId(@Param("systemTaskId") Long systemTaskId);

    List<ExportJobRecord> findQueued(@Param("limit") int limit);

    List<ExportJobRecord> findPageByRequester(
            @Param("requesterId") Long requesterId,
            @Param("exportType") ExportJobType exportType,
            @Param("status") ExportJobStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countByRequester(
            @Param("requesterId") Long requesterId,
            @Param("exportType") ExportJobType exportType,
            @Param("status") ExportJobStatus status
    );

    List<ExportJobRecord> findPendingReviews(
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countPendingReviews();

    int claim(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    int updateProgress(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("totalRows") long totalRows,
            @Param("processedRows") long processedRows
    );

    int queueAfterReview(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    int rejectAfterReview(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("reviewedAt") LocalDateTime reviewedAt
    );

    int succeed(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("resultFileId") Long resultFileId,
            @Param("totalRows") long totalRows,
            @Param("processedRows") long processedRows,
            @Param("completedAt") LocalDateTime completedAt
    );

    int fail(
            @Param("id") Long id,
            @Param("expectedVersion") Long expectedVersion,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("completedAt") LocalDateTime completedAt
    );
}
