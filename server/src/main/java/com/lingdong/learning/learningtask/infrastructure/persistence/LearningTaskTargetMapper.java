package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务原始目标持久化边界。 */
@Mapper
public interface LearningTaskTargetMapper {
    List<LearningTaskTarget> findByTaskId(@Param("taskId") Long taskId);

    int insert(@Param("target") LearningTaskTarget target);

    int deleteByTaskId(@Param("taskId") Long taskId);
}
