package com.lingdong.learning.learningtask.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 昨日任务复制批次、条目和候选任务持久化边界。 */
@Mapper
public interface PreviousDayTaskCopyMapper {
    List<TaskCopySourceRow> findSourceTasks(
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId,
            @Param("sourceDate") LocalDate sourceDate
    );

    List<String> findDuplicateTitles(
            @Param("parentUserId") Long parentUserId,
            @Param("studentId") Long studentId,
            @Param("sourceDate") LocalDate sourceDate,
            @Param("targetDate") LocalDate targetDate
    );

    TaskCopyBatchRow findBatchByStudentAndTargetDate(
            @Param("studentId") Long studentId,
            @Param("targetDate") LocalDate targetDate
    );

    TaskCopyBatchRow findBatchById(@Param("id") Long id);

    TaskCopyBatchRow findBatchByIdForUpdate(@Param("id") Long id);

    List<TaskCopyItemRow> findItemsByBatchId(@Param("batchId") Long batchId);

    TaskCopyItemRow findItemByIdForUpdate(@Param("id") Long id);

    int insertBatch(@Param("batch") TaskCopyBatchRow batch);

    int insertItem(@Param("item") TaskCopyItemRow item);

    int markItemSuccess(
            @Param("id") Long id,
            @Param("targetTaskId") Long targetTaskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("incrementRetry") boolean incrementRetry
    );

    int markItemFailed(
            @Param("id") Long id,
            @Param("failureCode") String failureCode,
            @Param("failureMessage") String failureMessage,
            @Param("expectedStatus") String expectedStatus,
            @Param("incrementRetry") boolean incrementRetry
    );

    TaskCopyCountRow countItems(@Param("batchId") Long batchId);

    int updateBatchSummary(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("successCount") int successCount,
            @Param("failureCount") int failureCount,
            @Param("completed") boolean completed
    );
}
