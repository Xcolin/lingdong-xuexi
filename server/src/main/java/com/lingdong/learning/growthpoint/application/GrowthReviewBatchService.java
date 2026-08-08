package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthReviewGenerationSource;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** 分批生成日、周、月成长复盘，并对前一日执行幂等回补。 */
@Service
public class GrowthReviewBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrowthReviewBatchService.class);
    private static final String DAILY_FEATURE_CODE = "DAILY_GROWTH_REVIEW";
    private static final String PERIODIC_FEATURE_CODE = "PERIODIC_GROWTH_REPORT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final GrowthReviewMapper reviewMapper;
    private final GrowthReviewGenerationService generationService;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;
    private final int batchSize;

    public GrowthReviewBatchService(
            GrowthReviewMapper reviewMapper,
            GrowthReviewGenerationService generationService,
            FeatureAccessService featureAccessService,
            Clock clock,
            @Value("${lingdong.growth-review.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("成长复盘生成批次大小必须在 1 至 1000 之间");
        }
        this.reviewMapper = reviewMapper;
        this.generationService = generationService;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int processDaily() {
        LocalDateTime now = now();
        return process(
                GrowthReviewPeriodType.DAY, now.toLocalDate(), now.toLocalDate(),
                GrowthReviewGenerationSource.AUTO, now, DAILY_FEATURE_CODE);
    }

    public int processPreviousDayBackfill() {
        LocalDateTime now = now();
        LocalDate date = now.toLocalDate().minusDays(1);
        return process(
                GrowthReviewPeriodType.DAY, date, date,
                GrowthReviewGenerationSource.BACKFILL, now, DAILY_FEATURE_CODE);
    }

    public int processPreviousWeek() {
        LocalDateTime now = now();
        LocalDate currentMonday = now.toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate periodStart = currentMonday.minusWeeks(1);
        return process(
                GrowthReviewPeriodType.WEEK, periodStart, periodStart.plusDays(6),
                GrowthReviewGenerationSource.AUTO, now, PERIODIC_FEATURE_CODE);
    }

    public int processPreviousMonth() {
        LocalDateTime now = now();
        LocalDate periodStart = now.toLocalDate().withDayOfMonth(1).minusMonths(1);
        return process(
                GrowthReviewPeriodType.MONTH, periodStart,
                periodStart.with(TemporalAdjusters.lastDayOfMonth()),
                GrowthReviewGenerationSource.AUTO, now, PERIODIC_FEATURE_CODE);
    }

    private int process(
            GrowthReviewPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            GrowthReviewGenerationSource source,
            LocalDateTime cutoff,
            String featureCode
    ) {
        if (!featureAccessService.isEnabled(featureCode, null)) {
            return 0;
        }
        int processed = 0;
        long afterId = 0L;
        List<Long> studentIds;
        do {
            studentIds = reviewMapper.findEnabledStudentIdsAfter(afterId, batchSize);
            for (Long studentId : studentIds) {
                afterId = studentId;
                try {
                    generationService.generate(
                            studentId, periodType, periodStart, periodEnd, source, cutoff);
                    processed++;
                } catch (RuntimeException exception) {
                    LOGGER.warn(
                            "成长复盘批量生成失败，已跳过当前学生并继续处理。studentId={}, periodType={}, periodStart={}, periodEnd={}",
                            studentId, periodType, periodStart, periodEnd, exception);
                }
            }
        } while (studentIds.size() == batchSize);
        return processed;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
    }
}
