package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 按任务来源和学生关系裁剪审核候选人。 */
@Mapper
public interface TaskReviewerMapper {
    List<ReviewerOptionRow> findOptions(
            @Param("sourceType") LearningTaskSourceType sourceType,
            @Param("sourceOrganizationId") Long sourceOrganizationId,
            @Param("studentId") Long studentId,
            @Param("currentReviewerId") Long currentReviewerId
    );
}
