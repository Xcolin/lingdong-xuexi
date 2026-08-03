package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 任务标签持久化边界。 */
@Mapper
public interface LearningTaskTagMapper {
    List<String> findCodesByTaskId(@Param("taskId") Long taskId);

    List<LearningTaskTagRow> findByTaskIds(@Param("taskIds") List<Long> taskIds);

    int insert(@Param("tag") LearningTaskTag tag);

    int deleteByTaskId(@Param("taskId") Long taskId);
}
