package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.FamilyTaskCheckInRow;
import com.lingdong.learning.learningtask.domain.TaskCheckIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 学生任务打卡持久化边界。 */
@Mapper
public interface TaskCheckInMapper {
    int nextSubmissionNo(@Param("assignmentId") Long assignmentId);

    int insert(@Param("checkIn") TaskCheckIn checkIn);

    TaskCheckIn findLatestSubmittedForUpdate(@Param("assignmentId") Long assignmentId);

    TaskCheckIn findLatestApprovedForUpdate(@Param("assignmentId") Long assignmentId);

    int reject(
            @Param("id") Long id,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reviewedAt") java.time.LocalDateTime reviewedAt,
            @Param("reviewComment") String reviewComment
    );

    int approve(
            @Param("id") Long id,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reviewedAt") java.time.LocalDateTime reviewedAt,
            @Param("reviewComment") String reviewComment
    );

    int reopenApproved(
            @Param("id") Long id,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reopenedAt") java.time.LocalDateTime reopenedAt
    );

    /** 家长任务详情打卡记录流（02-06）：按任务聚合全部 assignment 的已提交/已通过打卡。 */
    List<FamilyTaskCheckInRow> findFamilyRecords(@Param("taskId") Long taskId, @Param("limit") int limit);
}
