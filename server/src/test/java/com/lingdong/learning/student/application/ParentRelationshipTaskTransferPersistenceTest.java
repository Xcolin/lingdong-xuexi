package com.lingdong.learning.student.application;

import org.junit.jupiter.api.BeforeEach;
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
class ParentRelationshipTaskTransferPersistenceTest {
    private static final long OLD_PARENT_ID = 8910000000000000501L;
    private static final long NEW_PARENT_ID = 8910000000000000502L;
    private static final long STUDENT_ID = 8910000000000000503L;
    private static final long FAMILY_PENDING_ID = 8910000000000000504L;
    private static final long FAMILY_COMPLETED_ID = 8910000000000000505L;
    private static final long TEACHER_PENDING_ID = 8910000000000000506L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ParentRelationshipTaskTransferService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, mobile, user_type, status)
                values (?, 'transfer_old_parent', '原主家长', '13800000101', 'FAMILY', 'ENABLED'),
                       (?, 'transfer_new_parent', '新主家长', '13800000102', 'FAMILY', 'ENABLED')
                """, OLD_PARENT_ID, NEW_PARENT_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, grade_code, status)
                values (?, '转交测试学生', 'GRADE_3', 'ENABLED')
                """, STUDENT_ID);
        insertTask(FAMILY_PENDING_ID, "FAMILY", "家庭待审核任务");
        insertTask(FAMILY_COMPLETED_ID, "FAMILY", "家庭已完成任务");
        insertTask(TEACHER_PENDING_ID, "TEACHER", "教师待审核任务");
        insertAssignment(FAMILY_PENDING_ID, "FAMILY", "PENDING_REVIEW");
        insertAssignment(FAMILY_COMPLETED_ID, "FAMILY", "COMPLETED");
        insertAssignment(TEACHER_PENDING_ID, "TEACHER", "PENDING_REVIEW");
    }

    @Test
    void transfersOnlyFamilyPendingReviewForTargetStudent() {
        int transferred = service.transferPendingFamilyReviews(
                STUDENT_ID, OLD_PARENT_ID, NEW_PARENT_ID, OLD_PARENT_ID);

        assertThat(transferred).isEqualTo(1);
        assertThat(reviewer(FAMILY_PENDING_ID)).isEqualTo(NEW_PARENT_ID);
        assertThat(reviewer(FAMILY_COMPLETED_ID)).isEqualTo(OLD_PARENT_ID);
        assertThat(reviewer(TEACHER_PENDING_ID)).isEqualTo(OLD_PARENT_ID);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task_reviewer_transfer where assignment_id = ? and transfer_reason = ?",
                Integer.class, FAMILY_PENDING_ID, "主家长关系变更")).isEqualTo(1);
    }

    private void insertTask(long id, String sourceType, String title) {
        jdbcTemplate.update("""
                insert into learn_task (
                    id, source_type, creator_user_id, title, difficulty_level,
                    base_points, duration_minutes, scheduled_date, reviewer_user_id, status
                ) values (?, ?, ?, ?, 1, 10, 30, ?, ?, 'PUBLISHED')
                """, id, sourceType, OLD_PARENT_ID, title, LocalDate.of(2026, 8, 9), OLD_PARENT_ID);
    }

    private void insertAssignment(long id, String sourceType, String status) {
        jdbcTemplate.update("""
                insert into learn_task_assignment (
                    id, task_id, student_id, source_type, current_status,
                    current_reviewer_id, scheduled_date, due_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, id, STUDENT_ID, sourceType, status, OLD_PARENT_ID,
                LocalDate.of(2026, 8, 9), LocalDateTime.of(2026, 8, 9, 20, 0));
    }

    private long reviewer(long assignmentId) {
        return jdbcTemplate.queryForObject(
                "select current_reviewer_id from learn_task_assignment where id = ?",
                Long.class, assignmentId);
    }
}
