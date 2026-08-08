package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewCategoryRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDailyTrendRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewDetailRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSummaryRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewSupplementRow;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 学生本人和主家长的成长复盘安全查询及补录服务。 */
@Service
public class GrowthReviewQueryService {
    private static final String DAILY_FEATURE_CODE = "DAILY_GROWTH_REVIEW";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final GrowthReviewMapper reviewMapper;
    private final CurrentStudentAccessService currentStudentAccessService;
    private final ParentStudentMapper parentStudentMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public GrowthReviewQueryService(
            GrowthReviewMapper reviewMapper,
            CurrentStudentAccessService currentStudentAccessService,
            ParentStudentMapper parentStudentMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.reviewMapper = reviewMapper;
        this.currentStudentAccessService = currentStudentAccessService;
        this.parentStudentMapper = parentStudentMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GrowthReviewPage findMyReviews(
            AuthenticatedUser currentUser,
            GrowthReviewPeriodType periodType,
            int page,
            int pageSize
    ) {
        return findPage(requireMiniappStudent(currentUser).id(), periodType, page, pageSize);
    }

    @Transactional(readOnly = true)
    public GrowthReviewPage findChildReviews(
            AuthenticatedUser currentUser,
            Long studentId,
            GrowthReviewPeriodType periodType,
            int page,
            int pageSize
    ) {
        requireAccessibleChild(currentUser, studentId);
        return findPage(studentId, periodType, page, pageSize);
    }

    @Transactional(readOnly = true)
    public GrowthReviewDetailView findMyReview(
            AuthenticatedUser currentUser,
            Long reviewId
    ) {
        return requireDetail(requireMiniappStudent(currentUser).id(), reviewId);
    }

    @Transactional(readOnly = true)
    public GrowthReviewDetailView findChildReview(
            AuthenticatedUser currentUser,
            Long studentId,
            Long reviewId
    ) {
        requireAccessibleChild(currentUser, studentId);
        return requireDetail(studentId, reviewId);
    }

    @Transactional
    public GrowthReviewSupplementView addMySupplement(
            AuthenticatedUser currentUser,
            Long reviewId,
            AddGrowthReviewSupplementCommand command
    ) {
        Student student = requireMiniappStudent(currentUser);
        return addSupplement(student.id(), reviewId, currentUser.userId(), "STUDENT", command);
    }

    @Transactional
    public GrowthReviewSupplementView addChildSupplement(
            AuthenticatedUser currentUser,
            Long studentId,
            Long reviewId,
            AddGrowthReviewSupplementCommand command
    ) {
        requireAccessibleChild(currentUser, studentId);
        return addSupplement(studentId, reviewId, currentUser.userId(), "PARENT", command);
    }

    private GrowthReviewPage findPage(
            Long studentId,
            GrowthReviewPeriodType periodType,
            int page,
            int pageSize
    ) {
        if (periodType == null) {
            throw new IllegalArgumentException("复盘周期类型不合法");
        }
        int validatedPage = requireRange(page, "页码", 1, 1_000_000);
        int validatedPageSize = requireRange(pageSize, "每页数量", 1, 100);
        List<GrowthReviewSummaryView> items = reviewMapper.findCurrentByStudent(
                        studentId, periodType,
                        (validatedPage - 1) * validatedPageSize, validatedPageSize).stream()
                .map(this::toSummary)
                .toList();
        return new GrowthReviewPage(
                items, validatedPage, validatedPageSize,
                reviewMapper.countCurrentByStudent(studentId, periodType));
    }

    private GrowthReviewDetailView requireDetail(Long studentId, Long reviewId) {
        if (reviewId == null || reviewId <= 0) {
            throw notFound();
        }
        GrowthReviewDetailRow row = reviewMapper.findCurrentDetail(studentId, reviewId);
        if (row == null) {
            throw notFound();
        }
        List<GrowthReviewCategoryView> categories = reviewMapper.findCategories(row.snapshotId())
                .stream().map(this::toCategory).toList();
        List<GrowthReviewDailyTrendView> trends = reviewMapper.findDailyTrends(row.snapshotId())
                .stream().map(this::toTrend).toList();
        List<GrowthReviewSupplementView> supplements = reviewMapper.findSupplements(row.reviewId())
                .stream().map(this::toSupplement).toList();
        return new GrowthReviewDetailView(
                row.reviewId(), row.studentId(), row.studentName(), row.periodType(),
                row.periodStart(), row.periodEnd(), row.snapshotId(), row.contentVersion(),
                row.taskTotalCount(), row.completedCount(), row.inProgressCount(),
                row.pendingOptimizationCount(), row.exemptedCount(), row.completionRate(),
                row.earnedPoints(), row.pauseCount(), row.dataCutoffAt(), row.generatedAt(),
                categories, trends, supplements);
    }

    private GrowthReviewSupplementView addSupplement(
            Long studentId,
            Long reviewId,
            Long editorUserId,
            String editorRole,
            AddGrowthReviewSupplementCommand command
    ) {
        GrowthReviewDetailView review = requireDetail(studentId, reviewId);
        if (review.periodType() != GrowthReviewPeriodType.DAY) {
            throw new IllegalStateException("仅每日成长复盘允许补录");
        }
        featureAccessService.requireEnabled(DAILY_FEATURE_CODE, null);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        if (today.isBefore(review.periodStart())
                || today.isAfter(review.periodEnd().plusDays(1))) {
            throw new IllegalStateException("已超过成长复盘补录时限");
        }
        if (command == null || command.supplementType() == null) {
            throw new IllegalArgumentException("补录类型不合法");
        }
        String content = normalizeContent(command.content());
        Long id = idGenerator.nextId();
        LocalDateTime now = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (reviewMapper.insertSupplement(
                id, reviewId, editorUserId, editorRole,
                command.supplementType(), content, now) != 1) {
            throw new IllegalStateException("成长复盘补录写入失败");
        }
        return new GrowthReviewSupplementView(
                id, editorUserId, editorRole, command.supplementType(), content, now);
    }

    private GrowthReviewSummaryView toSummary(GrowthReviewSummaryRow row) {
        return new GrowthReviewSummaryView(
                row.reviewId(), row.studentId(), row.studentName(), row.periodType(),
                row.periodStart(), row.periodEnd(), row.snapshotId(), row.contentVersion(),
                row.taskTotalCount(), row.completedCount(), row.inProgressCount(),
                row.pendingOptimizationCount(), row.exemptedCount(), row.completionRate(),
                row.earnedPoints(), row.pauseCount(), row.generatedAt());
    }

    private GrowthReviewCategoryView toCategory(GrowthReviewCategoryRow row) {
        return new GrowthReviewCategoryView(
                row.categoryCode(), row.taskCount(), row.completedCount());
    }

    private GrowthReviewDailyTrendView toTrend(GrowthReviewDailyTrendRow row) {
        return new GrowthReviewDailyTrendView(
                row.trendDate(), row.taskTotalCount(), row.completedCount(),
                row.inProgressCount(), row.pendingOptimizationCount(), row.completionRate(),
                row.earnedPoints(), row.pauseCount());
    }

    private GrowthReviewSupplementView toSupplement(GrowthReviewSupplementRow row) {
        return new GrowthReviewSupplementView(
                row.id(), row.editorUserId(), row.editorRole(), row.supplementType(),
                row.content(), row.supplementedAt());
    }

    private Student requireMiniappStudent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.MINIAPP) {
            throw new SystemOperationAccessDeniedException("仅小程序学生可访问本人成长复盘");
        }
        return currentStudentAccessService.require(currentUser);
    }

    private void requireAccessibleChild(AuthenticatedUser currentUser, Long studentId) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new SystemOperationAccessDeniedException("仅 Web 端主家长可访问孩子成长复盘");
        }
        if (studentId == null || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), studentId)) {
            throw notFound();
        }
    }

    private String normalizeContent(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) {
            throw new IllegalArgumentException("补录内容不合法");
        }
        return normalized;
    }

    private int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return value;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("成长复盘不存在或不可访问");
    }
}
