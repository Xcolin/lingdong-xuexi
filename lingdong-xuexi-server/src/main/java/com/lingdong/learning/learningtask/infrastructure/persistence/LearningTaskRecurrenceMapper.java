package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskRecurrence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 每日固定任务计划持久化边界。 */
@Mapper
public interface LearningTaskRecurrenceMapper {
    int insert(@Param("recurrence") LearningTaskRecurrence recurrence);

    LearningTaskRecurrence findByTaskId(@Param("taskId") Long taskId);

    LearningTaskRecurrence findByTaskIdForUpdate(@Param("taskId") Long taskId);

    LearningTaskRecurrence findByIdForUpdate(@Param("id") Long id);

    List<Long> findDueIdsAfter(
            @Param("afterId") Long afterId,
            @Param("businessDate") LocalDate businessDate,
            @Param("limit") int limit
    );

    int advanceGeneration(
            @Param("id") Long id,
            @Param("nextGenerationDate") LocalDate nextGenerationDate,
            @Param("status") com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus status,
            @Param("expectedVersion") int expectedVersion
    );

    int stop(
            @Param("id") Long id,
            @Param("stoppedByUserId") Long stoppedByUserId,
            @Param("stoppedAt") LocalDateTime stoppedAt,
            @Param("expectedVersion") int expectedVersion
    );
}
