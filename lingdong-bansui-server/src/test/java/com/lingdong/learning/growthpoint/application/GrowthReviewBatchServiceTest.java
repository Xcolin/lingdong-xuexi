package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthReviewGenerationSource;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GrowthReviewBatchServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private GrowthReviewMapper mapper;
    private GrowthReviewGenerationService generationService;
    private FeatureAccessService featureAccessService;
    private GrowthReviewBatchService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GrowthReviewMapper.class);
        generationService = mock(GrowthReviewGenerationService.class);
        featureAccessService = mock(FeatureAccessService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-10T13:00:00Z"), ZONE);
        service = new GrowthReviewBatchService(
                mapper, generationService, featureAccessService, clock, 2);
    }

    @Test
    void generatesDailyReviewsForAllEnabledStudentsWithKeysetPaging() {
        when(featureAccessService.isEnabled("DAILY_GROWTH_REVIEW", null)).thenReturn(true);
        when(mapper.findEnabledStudentIdsAfter(0L, 2)).thenReturn(List.of(11L, 22L));
        when(mapper.findEnabledStudentIdsAfter(22L, 2)).thenReturn(List.of(33L));

        assertThat(service.processDaily()).isEqualTo(3);

        LocalDate date = LocalDate.of(2026, 8, 10);
        LocalDateTime cutoff = date.atTime(21, 0);
        verify(generationService).generate(
                11L, GrowthReviewPeriodType.DAY, date, date,
                GrowthReviewGenerationSource.AUTO, cutoff);
        verify(generationService).generate(
                22L, GrowthReviewPeriodType.DAY, date, date,
                GrowthReviewGenerationSource.AUTO, cutoff);
        verify(generationService).generate(
                33L, GrowthReviewPeriodType.DAY, date, date,
                GrowthReviewGenerationSource.AUTO, cutoff);
    }

    @Test
    void generatesPreviousNaturalWeekMonthAndPreviousDayBackfill() {
        when(featureAccessService.isEnabled("PERIODIC_GROWTH_REPORT", null)).thenReturn(true);
        when(featureAccessService.isEnabled("DAILY_GROWTH_REVIEW", null)).thenReturn(true);
        when(mapper.findEnabledStudentIdsAfter(0L, 2)).thenReturn(List.of(11L));

        assertThat(service.processPreviousWeek()).isEqualTo(1);
        assertThat(service.processPreviousMonth()).isEqualTo(1);
        assertThat(service.processPreviousDayBackfill()).isEqualTo(1);

        verify(generationService).generate(
                11L, GrowthReviewPeriodType.WEEK,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9),
                GrowthReviewGenerationSource.AUTO, LocalDateTime.of(2026, 8, 10, 21, 0));
        verify(generationService).generate(
                11L, GrowthReviewPeriodType.MONTH,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                GrowthReviewGenerationSource.AUTO, LocalDateTime.of(2026, 8, 10, 21, 0));
        verify(generationService).generate(
                11L, GrowthReviewPeriodType.DAY,
                LocalDate.of(2026, 8, 9), LocalDate.of(2026, 8, 9),
                GrowthReviewGenerationSource.BACKFILL, LocalDateTime.of(2026, 8, 10, 21, 0));
    }

    @Test
    void skipsTheWholeBatchWhenTheCorrespondingFeatureIsDisabled() {
        when(featureAccessService.isEnabled("DAILY_GROWTH_REVIEW", null)).thenReturn(false);

        assertThat(service.processDaily()).isZero();

        verifyNoInteractions(generationService);
        verify(mapper, org.mockito.Mockito.never()).findEnabledStudentIdsAfter(0L, 2);
    }

    @Test
    void continuesWithTheNextStudentWhenOneReviewGenerationFails() {
        when(featureAccessService.isEnabled("DAILY_GROWTH_REVIEW", null)).thenReturn(true);
        when(mapper.findEnabledStudentIdsAfter(0L, 2)).thenReturn(List.of(11L, 22L));
        when(mapper.findEnabledStudentIdsAfter(22L, 2)).thenReturn(List.of());
        LocalDate date = LocalDate.of(2026, 8, 10);
        LocalDateTime cutoff = date.atTime(21, 0);
        when(generationService.generate(
                11L, GrowthReviewPeriodType.DAY, date, date,
                GrowthReviewGenerationSource.AUTO, cutoff))
                .thenThrow(new IllegalStateException("模拟单学生生成失败"));

        assertThat(service.processDaily()).isEqualTo(1);

        verify(generationService).generate(
                22L, GrowthReviewPeriodType.DAY, date, date,
                GrowthReviewGenerationSource.AUTO, cutoff);
    }
}
