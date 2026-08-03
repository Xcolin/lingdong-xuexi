package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.StudentTaskAssignmentQuery;
import com.lingdong.learning.learningtask.domain.LearningTaskAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

/** 按学生展开的任务实例持久化边界。 */
@Mapper
public interface LearningTaskAssignmentMapper {
    int insertBatch(@Param("assignments") List<LearningTaskAssignment> assignments);

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

    int transitionStatus(
            @Param("id") Long id,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("expectedVersion") int expectedVersion,
            @Param("transitionAt") LocalDateTime transitionAt,
            @Param("claimedAt") LocalDateTime claimedAt
    );
}
