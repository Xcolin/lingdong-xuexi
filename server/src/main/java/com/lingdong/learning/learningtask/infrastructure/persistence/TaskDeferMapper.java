package com.lingdong.learning.learningtask.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import com.lingdong.learning.learningtask.application.ManagedDeferCandidateQuery;
import com.lingdong.learning.learningtask.application.ManagedDeferCandidateView;

/** 待优化任务顺延候选和锁定查询。 */
@Mapper
public interface TaskDeferMapper {
    TaskDeferStateRow findStateForUpdate(@Param("assignmentId") Long assignmentId);

    List<Long> findAutomaticCandidateIdsAfter(
            @Param("afterId") Long afterId,
            @Param("sourceDate") LocalDate sourceDate,
            @Param("limit") int limit
    );

    List<ManagedDeferCandidateView> findManagedPage(
            @Param("query") ManagedDeferCandidateQuery query
    );

    long countManaged(@Param("query") ManagedDeferCandidateQuery query);
}
