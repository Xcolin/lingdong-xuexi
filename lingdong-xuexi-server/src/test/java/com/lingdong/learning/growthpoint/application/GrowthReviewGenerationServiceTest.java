package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReviewGenerationSource;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GrowthReviewGenerationServiceTest {
    private static final long PARENT_ID = 1_874_244_142_494_650_001L;
    private static final long STUDENT_ID = 1_874_244_142_494_650_002L;
    private static final long ACCOUNT_ID = 1_874_244_142_494_650_003L;
    private static final LocalDate REVIEW_DATE = LocalDate.of(2026, 8, 8);

    @Autowired private GrowthReviewGenerationService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpFacts() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'growth_review_parent', '复盘测试家长', 'PARENT', 'ENABLED')
                """, PARENT_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, status)
                values (?, '复盘测试学生', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                insert into growth_point_account
                    (id, student_id, total_points, available_points, version_no)
                values (?, ?, 15, 10, 0)
                """, ACCOUNT_ID, STUDENT_ID);

        createTask(10, 20, "READING", "COMPLETED");
        createTask(20, 10, "READING", "IN_PROGRESS");
        createTask(30, 30, "MATH", "NEEDS_IMPROVEMENT");
        createTask(40, 10, "MATH", "EXEMPT");

        long completedAssignmentId = id(10, 2);
        jdbcTemplate.update("""
                insert into growth_point_ledger
                    (id, account_id, student_id, source_assignment_id, source_type,
                     change_type, amount, available_delta, reviewer_user_id, occurred_at, remark,
                     source_task_id, base_points_snapshot, decay_percent, streak_days)
                values (?, ?, ?, ?, 'FAMILY', 'TASK_REWARD', 20, 20, ?, ?, '复盘测试奖励',
                        ?, 20, 0, 1)
                """, id(51, 1), ACCOUNT_ID, STUDENT_ID, completedAssignmentId,
                PARENT_ID, REVIEW_DATE.atTime(10, 0), id(10, 1));
        jdbcTemplate.update("""
                insert into growth_point_ledger
                    (id, account_id, student_id, source_assignment_id, source_type,
                     change_type, amount, available_delta, reviewer_user_id, occurred_at,
                     correction_of_id, remark)
                values (?, ?, ?, ?, 'FAMILY', 'CORRECTION', -5, -5, ?, ?, ?, '复盘测试纠错')
                """, id(52, 1), ACCOUNT_ID, STUDENT_ID, completedAssignmentId,
                PARENT_ID, REVIEW_DATE.atTime(11, 0), id(51, 1));
        long dormancyNoticeId = id(53, 2);
        jdbcTemplate.update("""
                insert into growth_point_dormancy_notice
                    (id, student_id, primary_parent_user_id, activity_baseline_at,
                     clear_due_at, delivery_status)
                values (?, ?, ?, ?, ?, 'PENDING')
                """, dormancyNoticeId, STUDENT_ID, PARENT_ID,
                REVIEW_DATE.minusDays(30).atTime(12, 0), REVIEW_DATE.atTime(12, 0));
        jdbcTemplate.update("""
                insert into growth_point_ledger
                    (id, account_id, student_id, change_type, amount, available_delta,
                     occurred_at, remark, source_dormancy_notice_id)
                values (?, ?, ?, 'DORMANCY_CLEAR', 0, -5, ?, '不计入累计获取', ?)
                """, id(53, 1), ACCOUNT_ID, STUDENT_ID,
                REVIEW_DATE.atTime(12, 0), dormancyNoticeId);
        jdbcTemplate.update("""
                insert into learn_task_pause
                    (id, assignment_id, pause_type, started_by_user_id, started_at, expires_at)
                values (?, ?, 'EMOTION', ?, ?, ?)
                """, id(54, 1), id(20, 2), PARENT_ID,
                REVIEW_DATE.atTime(14, 0), REVIEW_DATE.atTime(14, 10));
    }

    @Test
    void generatesIdempotentSnapshotAndVersionsLateFactsWithoutOverwritingHistory() {
        LocalDateTime cutoff = REVIEW_DATE.atTime(21, 0);

        GrowthReviewGenerationResult first = service.generate(
                STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.AUTO, cutoff);

        assertThat(first.created()).isTrue();
        assertThat(first.contentVersion()).isEqualTo(1);
        assertThat(first.reviewId().toString()).hasSize(19);
        assertThat(first.snapshotId().toString()).hasSize(19);
        assertSnapshot(first.snapshotId(), 4, 1, 1, 1, 1,
                new BigDecimal("0.5000"), 15L, 1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from growth_review_category_stat where snapshot_id = ?",
                Integer.class, first.snapshotId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from growth_review_daily_trend where snapshot_id = ?",
                Integer.class, first.snapshotId())).isEqualTo(1);

        GrowthReviewGenerationResult repeated = service.generate(
                STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.AUTO, cutoff.plusMinutes(5));
        assertThat(repeated.created()).isFalse();
        assertThat(repeated.snapshotId()).isEqualTo(first.snapshotId());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from growth_review_snapshot where review_id = ?",
                Integer.class, first.reviewId())).isEqualTo(1);

        jdbcTemplate.update("""
                insert into growth_point_ledger
                    (id, account_id, student_id, source_assignment_id, source_type,
                     change_type, amount, available_delta, reviewer_user_id, occurred_at, remark,
                     source_task_id, base_points_snapshot, decay_percent, streak_days)
                values (?, ?, ?, ?, 'FAMILY', 'TASK_REWARD', 10, 10, ?, ?, '迟到奖励',
                        ?, 10, 0, 1)
                """, id(55, 1), ACCOUNT_ID, STUDENT_ID, id(20, 2),
                PARENT_ID, REVIEW_DATE.atTime(20, 30), id(20, 1));

        GrowthReviewGenerationResult backfilled = service.generate(
                STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.BACKFILL, cutoff.plusHours(1));
        assertThat(backfilled.created()).isTrue();
        assertThat(backfilled.contentVersion()).isEqualTo(2);
        assertSnapshot(backfilled.snapshotId(), 4, 1, 1, 1, 1,
                new BigDecimal("0.5000"), 25L, 1);
        assertThat(jdbcTemplate.queryForObject(
                "select earned_points from growth_review_snapshot where id = ?",
                Long.class, first.snapshotId())).isEqualTo(15L);
    }

    @Test
    void excludesExemptTasksFromPeriodicRateAndDailyTrend() {
        for (var type : new GrowthReviewPeriodType[]{GrowthReviewPeriodType.WEEK, GrowthReviewPeriodType.MONTH}) {
            LocalDate start = type == GrowthReviewPeriodType.WEEK ? LocalDate.of(2026, 8, 3) : REVIEW_DATE.withDayOfMonth(1);
            LocalDate end = type == GrowthReviewPeriodType.WEEK ? LocalDate.of(2026, 8, 9) : REVIEW_DATE.withDayOfMonth(31);
            var result = service.generate(STUDENT_ID, type, start, end,
                    GrowthReviewGenerationSource.AUTO, end.plusDays(1).atStartOfDay());
            assertSnapshot(result.snapshotId(), 4, 1, 1, 1, 1, new BigDecimal("0.5000"), 15L, 1);
            assertThat(jdbcTemplate.queryForObject(
                    "select completion_rate from growth_review_daily_trend where snapshot_id=? and trend_date=?",
                    BigDecimal.class, result.snapshotId(), REVIEW_DATE)).isEqualByComparingTo("0.5000");
        }
    }

    @Test
    void onlyExemptAndInProgressTasksHaveZeroDenominator() {
        jdbcTemplate.update("update learn_task_assignment set current_status='EXEMPT' where student_id=? and current_status<>'IN_PROGRESS'", STUDENT_ID);
        var result = service.generate(STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.AUTO, REVIEW_DATE.atTime(21, 0));
        assertSnapshot(result.snapshotId(), 4, 0, 1, 0, 3, BigDecimal.ZERO, 15L, 1);
    }

    @Test
    void recalculationCreatesNewVersionAndPreservesLegacyRate() {
        var legacy = service.generate(STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.AUTO, REVIEW_DATE.atTime(21, 0));
        // 模拟升级前已存的旧口径快照；重算只能生成新版本。
        jdbcTemplate.update("update growth_review_snapshot set completion_rate=0.3333,fact_fingerprint=? where id=?",
                "0".repeat(64), legacy.snapshotId());
        var current = service.generate(STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.BACKFILL, REVIEW_DATE.atTime(22, 0));
        assertThat(current.contentVersion()).isEqualTo(2);
        assertSnapshot(current.snapshotId(), 4, 1, 1, 1, 1, new BigDecimal("0.5000"), 15L, 1);
        assertThat(jdbcTemplate.queryForObject("select completion_rate from growth_review_snapshot where id=?",
                BigDecimal.class, legacy.snapshotId())).isEqualByComparingTo("0.3333");
        assertThat(service.generate(STUDENT_ID, GrowthReviewPeriodType.DAY, REVIEW_DATE, REVIEW_DATE,
                GrowthReviewGenerationSource.BACKFILL, REVIEW_DATE.atTime(23, 0)).created()).isFalse();
    }

    private void createTask(int offset, int basePoints, String category, String status) {
        long taskId = id(offset, 1);
        long assignmentId = id(offset, 2);
        jdbcTemplate.update("""
                insert into learn_task
                    (id, source_type, creator_user_id, title, difficulty_level, base_points,
                     duration_minutes, scheduled_date, category_code, reviewer_user_id, status)
                values (?, 'FAMILY', ?, ?, 1, ?, 30, ?, ?, ?, 'PUBLISHED')
                """, taskId, PARENT_ID, "复盘任务" + offset, basePoints,
                REVIEW_DATE, category, PARENT_ID);
        jdbcTemplate.update("""
                insert into learn_task_assignment
                    (id, task_id, student_id, source_type, current_status,
                     current_reviewer_id, scheduled_date, due_at, completed_at, last_transition_at)
                values (?, ?, ?, 'FAMILY', ?, ?, ?, ?, ?, ?)
                """, assignmentId, taskId, STUDENT_ID, status, PARENT_ID, REVIEW_DATE,
                REVIEW_DATE.atTime(23, 59), "COMPLETED".equals(status) ? REVIEW_DATE.atTime(9, 0) : null,
                REVIEW_DATE.atTime(9, 0));
    }

    private void assertSnapshot(
            Long snapshotId, int total, int completed, int inProgress,
            int needsImprovement, int exempted, BigDecimal rate, long points, int pauses
    ) {
        var values = jdbcTemplate.queryForMap("""
                select task_total_count, completed_count, in_progress_count,
                    pending_optimization_count, exempted_count, completion_rate,
                    earned_points, pause_count
                from growth_review_snapshot where id = ?
                """, snapshotId);
        assertThat(values.get("task_total_count")).isEqualTo(total);
        assertThat(values.get("completed_count")).isEqualTo(completed);
        assertThat(values.get("in_progress_count")).isEqualTo(inProgress);
        assertThat(values.get("pending_optimization_count")).isEqualTo(needsImprovement);
        assertThat(values.get("exempted_count")).isEqualTo(exempted);
        assertThat((BigDecimal) values.get("completion_rate")).isEqualByComparingTo(rate);
        assertThat(values.get("earned_points")).isEqualTo(points);
        assertThat(values.get("pause_count")).isEqualTo(pauses);
    }

    private long id(int group, int suffix) {
        return 1_874_244_142_494_650_000L + group * 10L + suffix;
    }
}
