package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.StudentTaskAssignmentQuery;
import com.lingdong.learning.learningtask.domain.LearningTaskAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 按学生展开的任务实例持久化边界。 */
@Mapper
public interface LearningTaskAssignmentMapper {
    int insertBatch(@Param("assignments") List<LearningTaskAssignment> assignments);

    List<Long> findStudentIdsByTaskAndDate(
            @Param("taskId") Long taskId,
            @Param("scheduledDate") LocalDate scheduledDate
    );

    List<StudentTaskAssignmentRow> findPage(@Param("query") StudentTaskAssignmentQuery query);

    long count(@Param("query") StudentTaskAssignmentQuery query);

    StudentTaskAssignmentRow findByIdAndStudentId(
            @Param("id") Long id,
            @Param("studentId") Long studentId
    );

    TaskAssignmentStateRow findStateByIdAndStudentIdForUpdate(
            @Param("id") Long id,
            @Param("studentId") Long studentId
    );

    List<Long> findOverdueIdsAfter(
            @Param("afterId") Long afterId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit
    );

    TaskOverdueStateRow findOverdueStateForUpdate(
            @Param("id") Long id,
            @Param("cutoff") LocalDateTime cutoff
    );

    int transitionStatus(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("expectedVersion") int expectedVersion,
            @Param("transitionAt") LocalDateTime transitionAt,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    int transferReviewer(
            @Param("id") Long id,
            @Param("expectedReviewerId") Long expectedReviewerId,
            @Param("nextReviewerId") Long nextReviewerId,
            @Param("expectedVersion") int expectedVersion,
            @Param("transitionAt") LocalDateTime transitionAt
    );

    int deferAssignment(
            @Param("id") Long id,
            @Param("targetTaskId") Long targetTaskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("targetDate") LocalDate targetDate,
            @Param("dueAt") LocalDateTime dueAt,
            @Param("deferType") String deferType,
            @Param("operatorUserId") Long operatorUserId,
            @Param("expectedVersion") int expectedVersion
    );
}
