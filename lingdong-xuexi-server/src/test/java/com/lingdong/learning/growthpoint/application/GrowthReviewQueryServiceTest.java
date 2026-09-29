package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GrowthReviewQueryServiceTest {
    private static final long PARENT_ID = 1_874_244_142_494_660_001L;
    private static final long OTHER_PARENT_ID = 1_874_244_142_494_660_002L;
    private static final long SECONDARY_PARENT_ID = 1_874_244_142_494_660_007L;
    private static final long STUDENT_USER_ID = 1_874_244_142_494_660_003L;
    private static final long STUDENT_ID = 1_874_244_142_494_660_004L;
    private static final long REVIEW_ID = 1_874_244_142_494_660_005L;
    private static final long SNAPSHOT_ID = 1_874_244_142_494_660_006L;

    @Autowired private GrowthReviewQueryService service;
    @Autowired private GrowthReviewExportPreparationService exportPreparation;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthReviewMapper reviewMapper;
    @Autowired private com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper parentMapper;
    @Autowired private com.lingdong.learning.permission.application.PermissionDecisionService permissionService;

    private LocalDate today;
    private AuthenticatedUser parent;
    private AuthenticatedUser otherParent;
    private AuthenticatedUser secondaryParent;
    private AuthenticatedUser student;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();
        parent = user(PARENT_ID, AuthClientType.WEB, "PARENT");
        otherParent = user(OTHER_PARENT_ID, AuthClientType.WEB, "PARENT");
        secondaryParent = user(SECONDARY_PARENT_ID, AuthClientType.WEB, "PARENT");
        student = user(STUDENT_USER_ID, AuthClientType.MINIAPP, "STUDENT");
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'review_parent', '复盘家长', 'FAMILY', 'ENABLED'),
                       (?, 'review_other_parent', '其他家长', 'FAMILY', 'ENABLED'),
                       (?, 'review_secondary_parent', '复盘副家长', 'FAMILY', 'ENABLED'),
                       (?, 'review_student', '复盘学生账号', 'STUDENT', 'ENABLED')
                """, PARENT_ID, OTHER_PARENT_ID, SECONDARY_PARENT_ID, STUDENT_USER_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, student_user_id, status)
                values (?, '复盘学生', ?, 'ENABLED')
                """, STUDENT_ID, STUDENT_USER_ID);
        jdbcTemplate.update("""
                insert into edu_parent_student
                    (id, parent_user_id, student_id, relation_role, status, primary_scope_key)
                values (?, ?, ?, 'PRIMARY_GUARDIAN', 'ACTIVE', 'PRIMARY')
                """, id(10), PARENT_ID, STUDENT_ID);
        jdbcTemplate.update("""
                insert into edu_parent_student
                    (id, parent_user_id, student_id, relation_role, status, primary_scope_key)
                values (?, ?, ?, 'SECONDARY_GUARDIAN', 'ACTIVE', 'SECONDARY')
                """, id(11), SECONDARY_PARENT_ID, STUDENT_ID);
        createReview(REVIEW_ID, SNAPSHOT_ID, today);
    }

    @Test
    void readsCurrentSnapshotForStudentAndPrimaryParentButHidesItFromOthers() {
        GrowthReviewDetailView childView = service.findChildReview(parent, STUDENT_ID, REVIEW_ID);
        assertThat(childView.reviewId()).isEqualTo(REVIEW_ID);
        assertThat(childView.snapshotId()).isEqualTo(SNAPSHOT_ID);
        assertThat(childView.contentVersion()).isEqualTo(1);
        assertThat(childView.taskTotalCount()).isEqualTo(4);
        assertThat(childView.categories()).hasSize(1);
        assertThat(childView.dailyTrends()).hasSize(1);

        GrowthReviewDetailView selfView = service.findMyReview(student, REVIEW_ID);
        assertThat(selfView.studentId()).isEqualTo(STUDENT_ID);
        assertThat(service.findChildReviews(
                parent, STUDENT_ID, GrowthReviewPeriodType.DAY, 1, 20).total()).isEqualTo(1);

        assertThatThrownBy(() -> service.findChildReview(otherParent, STUDENT_ID, REVIEW_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void secondaryParentReadsReviewButCannotAppendSupplement() {
        assertThat(service.findChildReview(secondaryParent, STUDENT_ID, REVIEW_ID).reviewId())
                .isEqualTo(REVIEW_ID);

        assertThatThrownBy(() -> service.addChildSupplement(
                secondaryParent, STUDENT_ID, REVIEW_ID,
                new AddGrowthReviewSupplementCommand(
                        GrowthReviewSupplementType.INSIGHT, "副家长不能补录")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appendsUnlimitedSupplementsOnlyOnTheReviewDayOrNextDay() {
        GrowthReviewSupplementView parentSupplement = service.addChildSupplement(
                parent, STUDENT_ID, REVIEW_ID,
                new AddGrowthReviewSupplementCommand(
                        GrowthReviewSupplementType.INSIGHT, "今天阅读更专注"));
        GrowthReviewSupplementView studentSupplement = service.addMySupplement(
                student, REVIEW_ID,
                new AddGrowthReviewSupplementCommand(
                        GrowthReviewSupplementType.NEXT_PLAN, "明天先完成数学任务"));

        assertThat(parentSupplement.id().toString()).hasSize(19);
        assertThat(parentSupplement.editorRole()).isEqualTo("PARENT");
        assertThat(studentSupplement.editorRole()).isEqualTo("STUDENT");
        assertThat(service.findMyReview(student, REVIEW_ID).supplements())
                .extracting(GrowthReviewSupplementView::content)
                .containsExactly("今天阅读更专注", "明天先完成数学任务");

        long expiredReviewId = id(20);
        long expiredSnapshotId = id(21);
        createReview(expiredReviewId, expiredSnapshotId, today.minusDays(2));
        assertThatThrownBy(() -> service.addMySupplement(
                student, expiredReviewId,
                new AddGrowthReviewSupplementCommand(
                        GrowthReviewSupplementType.INSIGHT, "已经超过补录时限")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("补录时限");
    }

    @Test
    void keepsHistoryReadableWhenDailyGenerationIsDisabledButBlocksNewSupplements() {
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'DAILY_GROWTH_REVIEW' and scope_key = 'GLOBAL'
                """);

        assertThat(service.findMyReview(student, REVIEW_ID).reviewId()).isEqualTo(REVIEW_ID);
        assertThatThrownBy(() -> service.addMySupplement(
                student, REVIEW_ID,
                new AddGrowthReviewSupplementCommand(
                        GrowthReviewSupplementType.INSIGHT, "开关关闭后不可补录")))
                .isInstanceOf(FeatureDisabledException.class);
    }

    @Test
    void preparesCompleteRangeInStableOrderAndKeepsPreparedSupplementsUnchanged() {
        grantReviewRole(PARENT_ID, id(70));
        createReview(id(80), id(81), today.minusDays(1));
        var selection = new GrowthReviewExportSelection(STUDENT_ID, null, GrowthReviewPeriodType.DAY,
                today.minusDays(1), today);
        var prepared = exportPreparation.prepare(parent, selection);
        assertThat(prepared.reports()).extracting(GrowthReviewDetailView::periodStart)
                .containsExactly(today.minusDays(1), today);
        assertThatThrownBy(() -> prepared.reports().clear()).isInstanceOf(UnsupportedOperationException.class);
        service.addChildSupplement(parent, STUDENT_ID, REVIEW_ID,
                new AddGrowthReviewSupplementCommand(GrowthReviewSupplementType.INSIGHT, "准备之后追加"));
        assertThat(prepared.reports().get(1).supplements()).isEmpty();
        assertThat(exportPreparation.prepare(parent, selection).reports().get(1).supplements()).hasSize(1);
    }

    @Test
    void preparesSingleReviewForActiveSecondaryParentButRejectsUnrelatedParent() {
        grantReviewRole(SECONDARY_PARENT_ID, id(71));
        grantReviewRole(OTHER_PARENT_ID, id(72));
        var selection = new GrowthReviewExportSelection(STUDENT_ID, REVIEW_ID, null, null, null);
        assertThat(exportPreparation.prepare(secondaryParent, selection).reports()).hasSize(1);
        assertThatThrownBy(() -> exportPreparation.prepare(otherParent, selection))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsMixedSelectionEmptyIntervalAndRevokedPermission() {
        assertThatThrownBy(() -> new GrowthReviewExportSelection(STUDENT_ID, REVIEW_ID,
                GrowthReviewPeriodType.DAY, today, today)).isInstanceOf(IllegalArgumentException.class);
        grantReviewRole(PARENT_ID, id(73));
        assertThatThrownBy(() -> exportPreparation.prepare(parent, new GrowthReviewExportSelection(
                STUDENT_ID, null, GrowthReviewPeriodType.DAY, today.minusDays(10), today.minusDays(9))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("没有");
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", PARENT_ID);
        // 直接 JDBC 改授权后模拟下一请求，避免复用当前测试事务的 MyBatis 一级缓存。
        sqlSession.clearCache();
        assertThatThrownBy(() -> exportPreparation.prepare(parent,
                new GrowthReviewExportSelection(STUDENT_ID, REVIEW_ID, null, null, null)))
                .isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
    }

    @Test
    void rejectsOversizedBatchInsteadOfSilentlyTruncating() {
        grantReviewRole(PARENT_ID, id(74));
        createReview(id(80), id(81), today.minusDays(1));
        var limited = new GrowthReviewExportPreparationService(reviewMapper, service, parentMapper, permissionService, 1);
        assertThatThrownBy(() -> limited.prepare(parent, new GrowthReviewExportSelection(STUDENT_ID, null,
                GrowthReviewPeriodType.DAY, today.minusDays(1), today)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("保护上限");
    }

    @Test
    void selectsWholeWeeklyPeriodRatherThanPartialOverlap() {
        grantReviewRole(PARENT_ID, id(75));
        jdbcTemplate.update("update growth_review set period_type='WEEK',period_end=? where id=?", today.plusDays(6), REVIEW_ID);
        assertThatThrownBy(() -> exportPreparation.prepare(parent, new GrowthReviewExportSelection(STUDENT_ID, null,
                GrowthReviewPeriodType.WEEK, today, today.plusDays(5))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("没有");
        assertThat(exportPreparation.prepare(parent, new GrowthReviewExportSelection(STUDENT_ID, null,
                GrowthReviewPeriodType.WEEK, today, today.plusDays(6))).reports()).hasSize(1);
    }

    private void grantReviewRole(long userId, long relationId) {
        jdbcTemplate.update("""
                insert into sys_user_role(id,user_id,role_id,organization_scope_key)
                select ?,?,id,'GLOBAL' from sys_role where role_code='PARENT'
                """, relationId, userId);
    }

    private void createReview(long reviewId, long snapshotId, LocalDate reviewDate) {
        LocalDateTime generatedAt = reviewDate.atTime(21, 0);
        jdbcTemplate.update("""
                insert into growth_review
                    (id, student_id, period_type, period_start, period_end, status,
                     created_at, updated_at)
                values (?, ?, 'DAY', ?, ?, 'FINAL', ?, ?)
                """, reviewId, STUDENT_ID, reviewDate, reviewDate, generatedAt, generatedAt);
        jdbcTemplate.update("""
                insert into growth_review_snapshot
                    (id, review_id, content_version, task_total_count, completed_count,
                     in_progress_count, pending_optimization_count, exempted_count,
                     completion_rate, earned_points, pause_count, generation_source,
                     fact_fingerprint, data_cutoff_at, generated_at)
                values (?, ?, 1, 4, 1, 1, 1, 1, 0.3333, 15, 1, 'AUTO',
                        ?, ?, ?)
                """, snapshotId, reviewId, "a".repeat(64), generatedAt, generatedAt);
        jdbcTemplate.update(
                "update growth_review set current_snapshot_id = ? where id = ?",
                snapshotId, reviewId);
        jdbcTemplate.update("""
                insert into growth_review_category_stat
                    (id, snapshot_id, category_code, task_count, completed_count)
                values (?, ?, 'READING', 2, 1)
                """, id((int) (reviewId % 100) + 30), snapshotId);
        jdbcTemplate.update("""
                insert into growth_review_daily_trend
                    (id, snapshot_id, trend_date, task_total_count, completed_count,
                     in_progress_count, pending_optimization_count, completion_rate,
                     earned_points, pause_count)
                values (?, ?, ?, 4, 1, 1, 1, 0.3333, 15, 1)
                """, id((int) (reviewId % 100) + 40), snapshotId, reviewDate);
    }

    private AuthenticatedUser user(long userId, AuthClientType clientType, String roleCode) {
        return new AuthenticatedUser(userId, id((int) (userId % 100) + 50),
                "review_user_" + userId, "复盘用户", clientType, List.of(roleCode));
    }

    private long id(int offset) {
        return 1_874_244_142_494_661_000L + offset;
    }
}
