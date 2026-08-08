package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.LearningTaskQuery;
import com.lingdong.learning.learningtask.domain.LearningTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;

/** 学习任务定义持久化边界。 */
@Mapper
public interface LearningTaskMapper {
    LearningTask findById(@Param("id") Long id);

    LearningTask findByIdForUpdate(@Param("id") Long id);

    List<LearningTask> findPage(@Param("query") LearningTaskQuery query);

    long count(@Param("query") LearningTaskQuery query);

    int insert(@Param("task") LearningTask task);

    int updateDraft(@Param("task") LearningTask task);

    int markPublished(@Param("id") Long id);

    int insertDeferredCopy(
            @Param("targetTaskId") Long targetTaskId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("targetDate") LocalDate targetDate
    );

    int insertPreviousDayCopy(
            @Param("targetTaskId") Long targetTaskId,
            @Param("sourceTaskId") Long sourceTaskId,
            @Param("targetDate") LocalDate targetDate,
            @Param("parentUserId") Long parentUserId
    );
}
