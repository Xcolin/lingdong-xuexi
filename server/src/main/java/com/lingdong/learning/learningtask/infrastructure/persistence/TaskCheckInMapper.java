package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskCheckIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 学生任务打卡持久化边界。 */
@Mapper
public interface TaskCheckInMapper {
    int nextSubmissionNo(@Param("assignmentId") Long assignmentId);

    int insert(@Param("checkIn") TaskCheckIn checkIn);

    TaskCheckIn findLatestSubmittedForUpdate(@Param("assignmentId") Long assignmentId);

    int reject(
            @Param("id") Long id,
            @Param("reviewerUserId") Long reviewerUserId,
            @Param("reviewedAt") java.time.LocalDateTime reviewedAt,
            @Param("reviewComment") String reviewComment
    );
}
