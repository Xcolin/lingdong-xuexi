package com.lingdong.learning.growthpoint.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 从任务、积分和暂停记录中读取复盘事实，不承担快照持久化。 */
@Mapper
public interface GrowthReviewFactMapper {
    GrowthReviewTaskFactRow aggregateTasks(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<GrowthReviewCategoryFactRow> aggregateCategories(
            @Param("studentId") Long studentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    long sumEarnedPoints(
            @Param("studentId") Long studentId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );

    int countPauses(
            @Param("studentId") Long studentId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endExclusive") LocalDateTime endExclusive
    );
}
