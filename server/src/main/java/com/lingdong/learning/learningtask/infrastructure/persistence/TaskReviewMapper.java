package com.lingdong.learning.learningtask.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

/** 当前审核人待办和审核锁定查询持久化边界。 */
@Mapper
public interface TaskReviewMapper {
    List<TaskReviewRow> findPage(
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long count(@Param("reviewerUserId") Long reviewerUserId);

    TaskReviewRow findByAssignmentIdAndReviewer(
            @Param("assignmentId") Long assignmentId,
            @Param("reviewerUserId") Long reviewerUserId
    );

    TaskReviewStateRow findStateForUpdate(
            @Param("assignmentId") Long assignmentId,
            @Param("reviewerUserId") Long reviewerUserId
    );

    List<TaskReviewStateRow> findPendingByReviewerForUpdate(
            @Param("reviewerUserId") Long reviewerUserId
    );

    List<TaskReviewStateRow> findPendingByReviewerAndClassForUpdate(
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("classOrganizationId") Long classOrganizationId
    );

    List<ParentRelationshipTaskReviewRow> findFamilyPendingByStudentForUpdate(
            @Param("studentId") Long studentId,
            @Param("reviewerUserId") Long reviewerUserId
    );

    int transferFamilyPendingReviewer(
            @Param("assignmentId") Long assignmentId,
            @Param("fromReviewerUserId") Long fromReviewerUserId,
            @Param("toReviewerUserId") Long toReviewerUserId,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
