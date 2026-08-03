package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskPause;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 任务暂停记录持久化边界。 */
@Mapper
public interface TaskPauseMapper {
    TaskPause findActive(@Param("assignmentId") Long assignmentId, @Param("now") LocalDateTime now);

    int closeExpired(@Param("assignmentId") Long assignmentId, @Param("now") LocalDateTime now);

    int insert(@Param("pause") TaskPause pause);

    int resume(@Param("id") Long id, @Param("resumedAt") LocalDateTime resumedAt);

    int terminateActive(@Param("assignmentId") Long assignmentId, @Param("terminatedAt") LocalDateTime terminatedAt);
}
