package com.lingdong.learning.learningtask.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}
