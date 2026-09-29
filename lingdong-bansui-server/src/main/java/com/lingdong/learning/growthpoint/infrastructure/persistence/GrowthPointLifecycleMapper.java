package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 积分衰减规则和有效连续完成历史的持久化查询边界。 */
@Mapper
public interface GrowthPointLifecycleMapper {
    List<LocalDate> findRecentEffectiveRewardDates(
            @Param("studentId") Long studentId,
            @Param("taskId") Long taskId,
            @Param("sourceType") LearningTaskSourceType sourceType,
            @Param("beforeDate") LocalDate beforeDate,
            @Param("limit") int limit
    );

    GrowthPointDecayRuleRow findApplicableDecayRule(
            @Param("streakDays") int streakDays,
            @Param("awardedAt") LocalDateTime awardedAt
    );

    GrowthPointDormancyStateRow findDormancyStateForUpdate(@Param("studentId") Long studentId);

    LocalDateTime findLatestEffectiveActivityAt(@Param("studentId") Long studentId);

    Long findDormancyNoticeId(
            @Param("studentId") Long studentId,
            @Param("activityBaselineAt") LocalDateTime activityBaselineAt
    );

    Long findPrimaryParentUserId(@Param("studentId") Long studentId);

    int insertDormancyState(@Param("studentId") Long studentId);

    int insertDormancyNotice(
            @Param("id") Long id,
            @Param("studentId") Long studentId,
            @Param("primaryParentUserId") Long primaryParentUserId,
            @Param("activityBaselineAt") LocalDateTime activityBaselineAt,
            @Param("clearDueAt") LocalDateTime clearDueAt,
            @Param("deliveryStatus") String deliveryStatus,
            @Param("now") LocalDateTime now
    );

    int markDormancyReminderCreated(
            @Param("studentId") Long studentId,
            @Param("now") LocalDateTime now,
            @Param("expectedVersion") int expectedVersion
    );

    int markDormancyCleared(
            @Param("studentId") Long studentId,
            @Param("now") LocalDateTime now,
            @Param("expectedVersion") int expectedVersion
    );

    int resetDormancyCycle(
            @Param("studentId") Long studentId,
            @Param("lastActivityAt") LocalDateTime lastActivityAt,
            @Param("reminderDueAt") LocalDateTime reminderDueAt,
            @Param("clearDueAt") LocalDateTime clearDueAt,
            @Param("now") LocalDateTime now,
            @Param("expectedVersion") int expectedVersion
    );

    List<Long> findDueDormancyStudentIdsAfter(
            @Param("afterId") long afterId,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );
}
