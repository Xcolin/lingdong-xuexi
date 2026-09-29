package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GrowthPointDecayIntegrationTest {
    private static final long PARENT_ID = 1_874_244_142_494_660_001L;
    private static final long STUDENT_ID = 1_874_244_142_494_660_002L;
    private static final long TASK_ID = 1_874_244_142_494_660_003L;
    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 8, 1);

    @Autowired private GrowthPointDecayService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void readsSevenEffectiveNaturalDaysAndAppliesTheEighthDayRule() {
        insertBaseFacts();
        for (int day = 0; day < 7; day++) {
            insertCompletedReward(day);
        }

        GrowthPointAwardCalculation calculation = service.calculate(
                STUDENT_ID, TASK_ID, LearningTaskSourceType.FAMILY,
                FIRST_DATE.plusDays(7), 20, LocalDateTime.of(2026, 8, 8, 12, 0));

        assertThat(calculation.basePoints()).isEqualTo(20);
        assertThat(calculation.awardedPoints()).isEqualTo(16);
        assertThat(calculation.streakDays()).isEqualTo(8);
        assertThat(calculation.decayPercent()).isEqualTo(20);
        assertThat(calculation.decayRuleId()).isEqualTo(1_874_244_142_494_646_401L);
    }

    private void insertBaseFacts() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'point_decay_parent', '衰减测试家长', 'PARENT', 'ENABLED')
                """, PARENT_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, status)
                values (?, '衰减测试学生', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                insert into growth_point_account
                    (id, student_id, total_points, available_points, version_no)
                values (?, ?, 140, 140, 0)
                """, STUDENT_ID, STUDENT_ID);
        jdbcTemplate.update("""
                insert into learn_task
                    (id, source_type, creator_user_id, title, difficulty_level, base_points,
                     duration_minutes, scheduled_date, reviewer_user_id, status)
                values (?, 'FAMILY', ?, '每日固定阅读', 2, 20, 30, ?, ?, 'PUBLISHED')
                """, TASK_ID, PARENT_ID, FIRST_DATE, PARENT_ID);
    }

    private void insertCompletedReward(int dayOffset) {
        long assignmentId = 1_874_244_142_494_661_000L + dayOffset;
        long ledgerId = 1_874_244_142_494_662_000L + dayOffset;
        LocalDate scheduledDate = FIRST_DATE.plusDays(dayOffset);
        LocalDateTime occurredAt = scheduledDate.atTime(20, 0);
        jdbcTemplate.update("""
                insert into learn_task_assignment
                    (id, task_id, student_id, source_type, current_status,
                     current_reviewer_id, scheduled_date, due_at, completed_at, last_transition_at)
                values (?, ?, ?, 'FAMILY', 'COMPLETED', ?, ?, ?, ?, ?)
                """, assignmentId, TASK_ID, STUDENT_ID, PARENT_ID, scheduledDate,
                scheduledDate.atTime(23, 59), occurredAt, occurredAt);
        jdbcTemplate.update("""
                insert into growth_point_ledger
                    (id, account_id, student_id, source_assignment_id, source_task_id,
                     source_type, change_type, amount, available_delta, reviewer_user_id,
                     occurred_at, remark, base_points_snapshot, decay_percent, streak_days)
                values (?, ?, ?, ?, ?, 'FAMILY', 'TASK_REWARD', 20, 20, ?, ?,
                        '连续任务历史奖励', 20, 0, ?)
                """, ledgerId, STUDENT_ID, STUDENT_ID, assignmentId, TASK_ID,
                PARENT_ID, occurredAt, dayOffset + 1);
    }
}
