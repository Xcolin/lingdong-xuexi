package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointDecayRuleRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 按同一任务的连续计划自然日计算透明、封顶的任务奖励衰减。 */
@Service
public class GrowthPointDecayService {
    private static final String FEATURE_CODE = "POINT_LIFECYCLE";
    private static final int HISTORY_LIMIT = 16;

    private final GrowthPointLifecycleMapper lifecycleMapper;
    private final FeatureAccessService featureAccessService;

    public GrowthPointDecayService(
            GrowthPointLifecycleMapper lifecycleMapper,
            FeatureAccessService featureAccessService
    ) {
        this.lifecycleMapper = lifecycleMapper;
        this.featureAccessService = featureAccessService;
    }

    public GrowthPointAwardCalculation calculate(
            Long studentId,
            Long taskId,
            LearningTaskSourceType sourceType,
            LocalDate scheduledDate,
            int basePoints,
            LocalDateTime awardedAt
    ) {
        if (!featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return fullAward(basePoints);
        }
        List<LocalDate> previousDates = lifecycleMapper.findRecentEffectiveRewardDates(
                studentId, taskId, sourceType, scheduledDate, HISTORY_LIMIT);
        int streakDays = calculateStreak(scheduledDate, previousDates);
        GrowthPointDecayRuleRow rule = lifecycleMapper.findApplicableDecayRule(streakDays, awardedAt);
        if (rule == null) {
            return new GrowthPointAwardCalculation(basePoints, basePoints, streakDays, 0, null);
        }
        int decayPercent = Math.min(rule.decayPercent(), 40);
        int awardedPoints = Math.multiplyExact(basePoints, 100 - decayPercent) / 100;
        return new GrowthPointAwardCalculation(
                basePoints, awardedPoints, streakDays, decayPercent, rule.id());
    }

    private int calculateStreak(LocalDate currentDate, List<LocalDate> previousDates) {
        int streakDays = 1;
        LocalDate expectedDate = currentDate.minusDays(1);
        for (LocalDate previousDate : previousDates) {
            if (!previousDate.equals(expectedDate)) {
                break;
            }
            streakDays++;
            expectedDate = expectedDate.minusDays(1);
        }
        return streakDays;
    }

    private GrowthPointAwardCalculation fullAward(int basePoints) {
        return new GrowthPointAwardCalculation(basePoints, basePoints, 1, 0, null);
    }
}
