package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PreviousDayTaskCopyPersistenceTest {
    private static final Long PARENT_ID = 8900000000000000001L;
    private static final Long STUDENT_ID = 8900000000000000002L;
    private static final Long SOURCE_TASK_ID = 8900000000000000004L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PreviousDayTaskCopyService copyService;
    @Autowired private PreviousDayTaskCopyTransactionService transactionService;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("""
                delete from learn_task_copy_item
                where batch_id in (
                    select id from learn_task_copy_batch where student_id = ?
                )
                """, STUDENT_ID);
        jdbcTemplate.update(
                "delete from learn_task_copy_batch where student_id = ?", STUDENT_ID);
        jdbcTemplate.update(
                "delete from learn_task_assignment where student_id = ?", STUDENT_ID);
        jdbcTemplate.update("""
                delete from learn_task_target
                where task_id in (
                    select id from learn_task where creator_user_id = ?
                )
                """, PARENT_ID);
        jdbcTemplate.update("""
                delete from learn_task_tag
                where task_id in (
                    select id from learn_task where creator_user_id = ?
                )
                """, PARENT_ID);
        jdbcTemplate.update(
                "delete from learn_task where origin_task_id = ?", SOURCE_TASK_ID);
        jdbcTemplate.update(
                "delete from learn_task where id = ?", SOURCE_TASK_ID);
        jdbcTemplate.update(
                "delete from edu_parent_student where student_id = ?", STUDENT_ID);
        jdbcTemplate.update("delete from edu_student where id = ?", STUDENT_ID);
        jdbcTemplate.update("delete from sys_user where id = ?", PARENT_ID);
    }

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'copy_parent', '复制测试家长', 'PLATFORM', 'ENABLED')
                """, PARENT_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, grade_code, status)
                values (?, '复制测试学生', 'GRADE_3', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                insert into edu_parent_student (
                    id, parent_user_id, student_id, relation_role, status, primary_scope_key
                ) values (?, ?, ?, 'PRIMARY_GUARDIAN', 'ACTIVE', 'PRIMARY')
                """, 8900000000000000003L, PARENT_ID, STUDENT_ID);
        jdbcTemplate.update("""
                insert into learn_task (
                    id, source_type, creator_user_id, title, difficulty_level, base_points,
                    duration_minutes, scheduled_date, category_code, remark, reviewer_user_id,
                    review_timeout_hours, recurrence_enabled, status, published_at
                ) values (?, 'FAMILY', ?, '昨日阅读', 2, 20, 30, ?, 'READING',
                    '完整复制备注', ?, 48, 0, 'PUBLISHED', current_timestamp)
                """, SOURCE_TASK_ID, PARENT_ID, today.minusDays(1), PARENT_ID);
        jdbcTemplate.update("""
                insert into learn_task_target (id, task_id, target_type, target_id)
                values (?, ?, 'STUDENT', ?)
                """, 8900000000000000005L, SOURCE_TASK_ID, STUDENT_ID);
        jdbcTemplate.update("""
                insert into learn_task_tag (id, task_id, tag_code)
                values (?, ?, 'READING')
                """, 8900000000000000006L, SOURCE_TASK_ID);
        jdbcTemplate.update("""
                insert into learn_task_assignment (
                    id, task_id, student_id, source_type, current_status,
                    current_reviewer_id, scheduled_date, due_at
                ) values (?, ?, ?, 'FAMILY', 'COMPLETED', ?, ?, ?)
                """, 8900000000000000007L, SOURCE_TASK_ID, STUDENT_ID, PARENT_ID,
                today.minusDays(1), today.minusDays(1).atTime(23, 59, 59));
    }

    @Test
    void copiesDefinitionTagSingleStudentTargetAndPendingAssignmentIdempotently() {
        AuthenticatedUser parent = new AuthenticatedUser(
                PARENT_ID, 8900000000000000008L, "copy_parent", "复制测试家长",
                AuthClientType.WEB, List.of("PARENT"));

        PreviousDayTaskCopyPreview preview = copyService.preview(parent, STUDENT_ID);
        TaskCopyBatchResult first = copyService.copy(parent, STUDENT_ID, false);
        TaskCopyBatchResult repeated = copyService.copy(parent, STUDENT_ID, false);

        assertThat(preview.candidateCount()).isEqualTo(1);
        assertThat(preview.duplicateTitles()).isEmpty();
        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(first.successCount()).isEqualTo(1);
        assertThat(repeated.batchId()).isEqualTo(first.batchId());
        Long targetTaskId = first.items().get(0).targetTaskId();
        assertThat(targetTaskId.toString()).hasSize(19);
        assertThat(jdbcTemplate.queryForMap("""
                select title, difficulty_level, base_points, duration_minutes, category_code,
                       remark, reviewer_user_id, review_timeout_hours, recurrence_enabled,
                       generation_type, origin_task_id
                from learn_task where id = ?
                """, targetTaskId))
                .containsEntry("TITLE", "昨日阅读")
                .containsEntry("DIFFICULTY_LEVEL", 2)
                .containsEntry("BASE_POINTS", 20)
                .containsEntry("DURATION_MINUTES", 30)
                .containsEntry("CATEGORY_CODE", "READING")
                .containsEntry("REMARK", "完整复制备注")
                .containsEntry("REVIEWER_USER_ID", PARENT_ID)
                .containsEntry("REVIEW_TIMEOUT_HOURS", 48)
                .containsEntry("RECURRENCE_ENABLED", false)
                .containsEntry("GENERATION_TYPE", "COPIED")
                .containsEntry("ORIGIN_TASK_ID", SOURCE_TASK_ID);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task_tag where task_id = ? and tag_code = 'READING'",
                Integer.class, targetTaskId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_target
                where task_id = ? and target_type = 'STUDENT' and target_id = ?
                """, Integer.class, targetTaskId, STUDENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_assignment
                where task_id = ? and student_id = ? and current_status = 'PENDING_CLAIM'
                """, Integer.class, targetTaskId, STUDENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task_copy_batch where student_id = ?",
                Integer.class, STUDENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task where generation_type = 'COPIED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void resumesPendingItemsWhenExistingBatchWasInterruptedBeforeProcessing() {
        LocalDate targetDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        PreparedTaskCopyBatch prepared = transactionService.prepare(
                PARENT_ID, STUDENT_ID, targetDate.minusDays(1), targetDate, false);
        AuthenticatedUser parent = new AuthenticatedUser(
                PARENT_ID, 8900000000000000008L, "copy_parent", "复制测试家长",
                AuthClientType.WEB, List.of("PARENT"));

        TaskCopyBatchResult resumed = copyService.copy(parent, STUDENT_ID, false);

        assertThat(prepared.itemIds()).hasSize(1);
        assertThat(resumed.status()).isEqualTo("COMPLETED");
        assertThat(resumed.successCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from learn_task where generation_type = 'COPIED'",
                Integer.class)).isEqualTo(1);
    }
}
