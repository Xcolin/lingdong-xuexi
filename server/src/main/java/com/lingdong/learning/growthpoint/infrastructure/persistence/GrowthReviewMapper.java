package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 成长复盘逻辑记录及不可变快照的写入边界。 */
@Mapper
public interface GrowthReviewMapper {
    GrowthReviewRow findForUpdate(
            @Param("studentId") Long studentId,
            @Param("periodType") GrowthReviewPeriodType periodType,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    int insertReview(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("periodType") GrowthReviewPeriodType periodType,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("now") LocalDateTime now
    );

    GrowthReviewSnapshotRow findCurrentSnapshot(@Param("reviewId") Long reviewId);

    int findMaxVersion(@Param("reviewId") Long reviewId);

    int insertSnapshot(@Param("snapshot") GrowthReviewSnapshotWrite snapshot);

    int insertCategories(@Param("rows") List<GrowthReviewCategoryWrite> rows);

    int insertDailyTrends(@Param("rows") List<GrowthReviewDailyTrendWrite> rows);

    int updateCurrentSnapshot(
            @Param("reviewId") Long reviewId,
            @Param("snapshotId") Long snapshotId,
            @Param("now") LocalDateTime now
    );

    List<GrowthReviewSummaryRow> findCurrentByStudent(
            @Param("studentId") Long studentId,
            @Param("periodType") GrowthReviewPeriodType periodType,
            @Param("offset") int offset,
            @Param("limit") int limit
    );

    long countCurrentByStudent(
            @Param("studentId") Long studentId,
            @Param("periodType") GrowthReviewPeriodType periodType
    );

    GrowthReviewDetailRow findCurrentDetail(
            @Param("studentId") Long studentId,
            @Param("reviewId") Long reviewId
    );

    List<GrowthReviewCategoryRow> findCategories(@Param("snapshotId") Long snapshotId);

    List<GrowthReviewDailyTrendRow> findDailyTrends(@Param("snapshotId") Long snapshotId);

    List<GrowthReviewSupplementRow> findSupplements(@Param("reviewId") Long reviewId);

    int insertSupplement(
            @Param("id") Long id,
            @Param("reviewId") Long reviewId,
            @Param("editorUserId") Long editorUserId,
            @Param("editorRole") String editorRole,
            @Param("supplementType") GrowthReviewSupplementType supplementType,
            @Param("content") String content,
            @Param("supplementedAt") LocalDateTime supplementedAt
    );

    List<Long> findEnabledStudentIdsAfter(
            @Param("afterId") Long afterId,
            @Param("limit") int limit
    );
}
