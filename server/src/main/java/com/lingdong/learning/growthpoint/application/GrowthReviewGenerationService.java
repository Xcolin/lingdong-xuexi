package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthReviewGenerationSource;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewCategoryFactRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewCategoryWrite;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDailyTrendWrite;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewFactMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSnapshotRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSnapshotWrite;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewTaskFactRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** 聚合稳定业务事实并生成不可变成长复盘快照。 */
@Service
public class GrowthReviewGenerationService {
    private static final String DAILY_FEATURE_CODE = "DAILY_GROWTH_REVIEW";
    private static final String PERIODIC_FEATURE_CODE = "PERIODIC_GROWTH_REPORT";

    private final GrowthReviewFactMapper factMapper;
    private final GrowthReviewMapper reviewMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;

    public GrowthReviewGenerationService(
            GrowthReviewFactMapper factMapper,
            GrowthReviewMapper reviewMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator
    ) {
        this.factMapper = factMapper;
        this.reviewMapper = reviewMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public GrowthReviewGenerationResult generate(
            Long studentId,
            GrowthReviewPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            GrowthReviewGenerationSource generationSource,
            LocalDateTime dataCutoffAt
    ) {
        validate(studentId, periodType, periodStart, periodEnd, generationSource, dataCutoffAt);
        featureAccessService.requireEnabled(featureCode(periodType), null);

        LocalDateTime startAt = periodStart.atStartOfDay();
        LocalDateTime periodEndExclusive = periodEnd.plusDays(1).atStartOfDay();
        LocalDateTime factEndExclusive = earlierOf(dataCutoffAt, periodEndExclusive);
        GrowthReviewTaskFactRow aggregate = factMapper.aggregateTasks(studentId, periodStart, periodEnd);
        if (aggregate == null) {
            aggregate = GrowthReviewTaskFactRow.empty();
        }
        List<GrowthReviewCategoryFactRow> categories = new ArrayList<>(
                factMapper.aggregateCategories(studentId, periodStart, periodEnd));
        categories.sort(Comparator.comparing(GrowthReviewCategoryFactRow::categoryCode));
        long earnedPoints = factMapper.sumEarnedPoints(studentId, startAt, factEndExclusive);
        int pauseCount = factMapper.countPauses(studentId, startAt, factEndExclusive);
        List<TrendFact> trends = buildDailyTrends(
                studentId, periodStart, periodEnd, dataCutoffAt);

        BigDecimal completionRate = completionRate(aggregate);
        String fingerprint = fingerprint(
                aggregate, completionRate, earnedPoints, pauseCount, categories, trends);
        GrowthReviewRow review = reviewMapper.findForUpdate(
                studentId, periodType, periodStart, periodEnd);
        LocalDateTime now = dataCutoffAt;
        if (review == null) {
            Long reviewId = idGenerator.nextId();
            requireSingleWrite(reviewMapper.insertReview(
                    reviewId, studentId, periodType, periodStart, periodEnd, now));
            review = new GrowthReviewRow(reviewId, null);
        }

        GrowthReviewSnapshotRow current = reviewMapper.findCurrentSnapshot(review.id());
        if (current != null && current.factFingerprint().equals(fingerprint)) {
            return new GrowthReviewGenerationResult(
                    review.id(), current.id(), current.contentVersion(), false);
        }

        int contentVersion = reviewMapper.findMaxVersion(review.id()) + 1;
        Long snapshotId = idGenerator.nextId();
        GrowthReviewSnapshotWrite snapshot = new GrowthReviewSnapshotWrite(
                snapshotId, review.id(), contentVersion,
                aggregate.taskTotalCount(), aggregate.completedCount(), aggregate.inProgressCount(),
                aggregate.pendingOptimizationCount(), aggregate.exemptedCount(), completionRate,
                earnedPoints, pauseCount, generationSource, fingerprint, dataCutoffAt, now);
        requireSingleWrite(reviewMapper.insertSnapshot(snapshot));

        List<GrowthReviewCategoryWrite> categoryWrites = categories.stream()
                .map(row -> new GrowthReviewCategoryWrite(
                        idGenerator.nextId(), snapshotId, row.categoryCode(),
                        row.taskCount(), row.completedCount()))
                .toList();
        if (!categoryWrites.isEmpty()) {
            requireAffectedRows(
                    reviewMapper.insertCategories(categoryWrites), categoryWrites.size());
        }

        List<GrowthReviewDailyTrendWrite> trendWrites = trends.stream()
                .map(row -> new GrowthReviewDailyTrendWrite(
                        idGenerator.nextId(), snapshotId, row.date(),
                        row.tasks().taskTotalCount(), row.tasks().completedCount(),
                        row.tasks().inProgressCount(), row.tasks().pendingOptimizationCount(),
                        row.completionRate(), row.earnedPoints(), row.pauseCount()))
                .toList();
        if (!trendWrites.isEmpty()) {
            requireAffectedRows(reviewMapper.insertDailyTrends(trendWrites), trendWrites.size());
        }
        requireSingleWrite(reviewMapper.updateCurrentSnapshot(review.id(), snapshotId, now));
        return new GrowthReviewGenerationResult(review.id(), snapshotId, contentVersion, true);
    }

    private List<TrendFact> buildDailyTrends(
            Long studentId,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDateTime dataCutoffAt
    ) {
        List<TrendFact> trends = new ArrayList<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            GrowthReviewTaskFactRow tasks = factMapper.aggregateTasks(studentId, date, date);
            if (tasks == null) {
                tasks = GrowthReviewTaskFactRow.empty();
            }
            LocalDateTime startAt = date.atStartOfDay();
            LocalDateTime endExclusive = earlierOf(dataCutoffAt, date.plusDays(1).atStartOfDay());
            long points = endExclusive.isAfter(startAt)
                    ? factMapper.sumEarnedPoints(studentId, startAt, endExclusive) : 0L;
            int pauses = endExclusive.isAfter(startAt)
                    ? factMapper.countPauses(studentId, startAt, endExclusive) : 0;
            trends.add(new TrendFact(date, tasks, completionRate(tasks), points, pauses));
        }
        return trends;
    }

    private BigDecimal completionRate(GrowthReviewTaskFactRow facts) {
        int denominator = facts.taskTotalCount() - facts.inProgressCount();
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(facts.completedCount())
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private String fingerprint(
            GrowthReviewTaskFactRow aggregate,
            BigDecimal completionRate,
            long earnedPoints,
            int pauseCount,
            List<GrowthReviewCategoryFactRow> categories,
            List<TrendFact> trends
    ) {
        StringBuilder canonical = new StringBuilder(512)
                .append(aggregate.taskTotalCount()).append('|')
                .append(aggregate.completedCount()).append('|')
                .append(aggregate.inProgressCount()).append('|')
                .append(aggregate.pendingOptimizationCount()).append('|')
                .append(aggregate.exemptedCount()).append('|')
                .append(completionRate.toPlainString()).append('|')
                .append(earnedPoints).append('|').append(pauseCount);
        for (GrowthReviewCategoryFactRow category : categories) {
            canonical.append("|C:").append(category.categoryCode()).append(':')
                    .append(category.taskCount()).append(':').append(category.completedCount());
        }
        for (TrendFact trend : trends) {
            canonical.append("|D:").append(trend.date()).append(':')
                    .append(trend.tasks().taskTotalCount()).append(':')
                    .append(trend.tasks().completedCount()).append(':')
                    .append(trend.tasks().inProgressCount()).append(':')
                    .append(trend.tasks().pendingOptimizationCount()).append(':')
                    .append(trend.completionRate().toPlainString()).append(':')
                    .append(trend.earnedPoints()).append(':').append(trend.pauseCount());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private String featureCode(GrowthReviewPeriodType periodType) {
        return periodType == GrowthReviewPeriodType.DAY
                ? DAILY_FEATURE_CODE : PERIODIC_FEATURE_CODE;
    }

    private LocalDateTime earlierOf(LocalDateTime left, LocalDateTime right) {
        return left.isBefore(right) ? left : right;
    }

    private void validate(
            Long studentId,
            GrowthReviewPeriodType periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            GrowthReviewGenerationSource generationSource,
            LocalDateTime dataCutoffAt
    ) {
        if (studentId == null || studentId <= 0) {
            throw new IllegalArgumentException("学生标识不合法");
        }
        if (periodType == null || periodStart == null || periodEnd == null
                || generationSource == null || dataCutoffAt == null) {
            throw new IllegalArgumentException("成长复盘生成参数不完整");
        }
        if (periodEnd.isBefore(periodStart) || dataCutoffAt.isBefore(periodStart.atStartOfDay())) {
            throw new IllegalArgumentException("成长复盘周期不合法");
        }
        if (periodType == GrowthReviewPeriodType.DAY && !periodStart.equals(periodEnd)) {
            throw new IllegalArgumentException("每日复盘必须使用同一自然日");
        }
    }

    private void requireSingleWrite(int affectedRows) {
        requireAffectedRows(affectedRows, 1);
    }

    private void requireAffectedRows(int affectedRows, int expectedRows) {
        if (affectedRows != expectedRows) {
            throw new IllegalStateException("成长复盘快照写入状态已变化");
        }
    }

    private record TrendFact(
            LocalDate date,
            GrowthReviewTaskFactRow tasks,
            BigDecimal completionRate,
            long earnedPoints,
            int pauseCount
    ) {
    }
}
