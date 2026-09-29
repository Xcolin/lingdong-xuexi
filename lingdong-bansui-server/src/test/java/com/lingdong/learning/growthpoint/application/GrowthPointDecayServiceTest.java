package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointDecayRuleRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLifecycleMapper;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GrowthPointDecayServiceTest {
    private GrowthPointLifecycleMapper mapper;
    private FeatureAccessService featureAccessService;
    private GrowthPointDecayService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GrowthPointLifecycleMapper.class);
        featureAccessService = mock(FeatureAccessService.class);
        service = new GrowthPointDecayService(mapper, featureAccessService);
    }

    @Test
    void appliesTwentyPercentFromTheEighthConsecutiveNaturalDay() {
        LocalDate currentDate = LocalDate.of(2026, 8, 8);
        when(featureAccessService.isEnabled("POINT_LIFECYCLE", null)).thenReturn(true);
        when(mapper.findRecentEffectiveRewardDates(
                11L, 22L, LearningTaskSourceType.FAMILY, currentDate, 16))
                .thenReturn(previousConsecutiveDates(currentDate, 7));
        GrowthPointDecayRuleRow rule = new GrowthPointDecayRuleRow(
                101L, 8, 20, LocalDateTime.of(2026, 1, 1, 0, 0), null, 1);
        when(mapper.findApplicableDecayRule(8, LocalDateTime.of(2026, 8, 8, 12, 0)))
                .thenReturn(rule);

        GrowthPointAwardCalculation result = service.calculate(
                11L, 22L, LearningTaskSourceType.FAMILY, currentDate,
                20, LocalDateTime.of(2026, 8, 8, 12, 0));

        assertThat(result).isEqualTo(new GrowthPointAwardCalculation(20, 16, 8, 20, 101L));
    }

    @Test
    void capsDecayAtFortyPercentFromTheSixteenthDay() {
        LocalDate currentDate = LocalDate.of(2026, 8, 16);
        when(featureAccessService.isEnabled("POINT_LIFECYCLE", null)).thenReturn(true);
        when(mapper.findRecentEffectiveRewardDates(
                11L, 22L, LearningTaskSourceType.ORGANIZATION, currentDate, 16))
                .thenReturn(previousConsecutiveDates(currentDate, 15));
        when(mapper.findApplicableDecayRule(16, LocalDateTime.of(2026, 8, 16, 12, 0)))
                .thenReturn(new GrowthPointDecayRuleRow(
                        102L, 16, 40, LocalDateTime.of(2026, 1, 1, 0, 0), null, 1));

        GrowthPointAwardCalculation result = service.calculate(
                11L, 22L, LearningTaskSourceType.ORGANIZATION, currentDate,
                30, LocalDateTime.of(2026, 8, 16, 12, 0));

        assertThat(result.awardedPoints()).isEqualTo(18);
        assertThat(result.streakDays()).isEqualTo(16);
        assertThat(result.decayPercent()).isEqualTo(40);
    }

    @Test
    void resetsToFullPointsWhenThePreviousNaturalDayIsMissing() {
        LocalDate currentDate = LocalDate.of(2026, 8, 8);
        when(featureAccessService.isEnabled("POINT_LIFECYCLE", null)).thenReturn(true);
        when(mapper.findRecentEffectiveRewardDates(
                11L, 22L, LearningTaskSourceType.FAMILY, currentDate, 16))
                .thenReturn(List.of(currentDate.minusDays(2)));

        GrowthPointAwardCalculation result = service.calculate(
                11L, 22L, LearningTaskSourceType.FAMILY, currentDate,
                20, LocalDateTime.of(2026, 8, 8, 12, 0));

        assertThat(result).isEqualTo(new GrowthPointAwardCalculation(20, 20, 1, 0, null));
    }

    @Test
    void awardsFullPointsWithoutReadingHistoryWhenLifecycleIsDisabled() {
        when(featureAccessService.isEnabled("POINT_LIFECYCLE", null)).thenReturn(false);

        GrowthPointAwardCalculation result = service.calculate(
                11L, 22L, LearningTaskSourceType.TEACHER, LocalDate.of(2026, 8, 8),
                10, LocalDateTime.of(2026, 8, 8, 12, 0));

        assertThat(result).isEqualTo(new GrowthPointAwardCalculation(10, 10, 1, 0, null));
        verifyNoInteractions(mapper);
    }

    private List<LocalDate> previousConsecutiveDates(LocalDate currentDate, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(currentDate::minusDays)
                .toList();
    }
}
