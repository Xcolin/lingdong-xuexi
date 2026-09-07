package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
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

/** 通过真实 H2 与 MyBatis 验证两类导出的筛选、上界和游标 SQL。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExportDatasetPersistenceTest {
    private static final long PARENT_ID = 1874244142494646950L;
    private static final long TARGET_USER_ID = 1874244142494646951L;
    private static final long STUDENT_ID = 1874244142494646952L;
    private static final long ACCOUNT_ID = 1874244142494646953L;
    private static final long TASK_ID = 1874244142494646954L;
    private static final long ASSIGNMENT_ID = 1874244142494646955L;
    private static final long LEDGER_ID_1 = 1874244142494646956L;
    private static final long LEDGER_ID_2 = 1874244142494646957L;
    private long auditId1;
    private long auditId2;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdGenerator idGenerator;
    @Autowired private GrowthPointExportMapper growthPointMapper;
    @Autowired private IamAuditExportMapper iamAuditMapper;

    @BeforeEach
    void insertFacts() {
        // 审计表会被其他集成测试写入，使用当前雪花序列保证本用例记录构成查询上界。
        auditId1 = idGenerator.nextId();
        auditId2 = idGenerator.nextId();
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'export_dataset_parent', '王家长', 'PARENT', 'ENABLED')
                """, PARENT_ID);
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'export_dataset_target', '李同学', 'PARENT', 'ENABLED')
                """, TARGET_USER_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, status)
                values (?, '小灵', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                insert into growth_point_account
                    (id, student_id, total_points, available_points, version_no)
                values (?, ?, 40, 40, 0)
                """, ACCOUNT_ID, STUDENT_ID);
        jdbcTemplate.update("""
                insert into learn_task
                    (id, source_type, creator_user_id, title, difficulty_level, base_points,
                     duration_minutes, scheduled_date, reviewer_user_id, status)
                values (?, 'FAMILY', ?, '晨读', 2, 20, 30, ?, ?, 'PUBLISHED')
                """, TASK_ID, PARENT_ID, LocalDate.of(2026, 9, 2), PARENT_ID);
        jdbcTemplate.update("""
                insert into learn_task_assignment
                    (id, task_id, student_id, source_type, current_status, current_reviewer_id,
                     scheduled_date, due_at, completed_at, last_transition_at)
                values (?, ?, ?, 'FAMILY', 'COMPLETED', ?, ?, ?, ?, ?)
                """, ASSIGNMENT_ID, TASK_ID, STUDENT_ID, PARENT_ID,
                LocalDate.of(2026, 9, 2), LocalDateTime.of(2026, 9, 2, 23, 59),
                LocalDateTime.of(2026, 9, 2, 9, 0), LocalDateTime.of(2026, 9, 2, 9, 0));
        insertLedger(LEDGER_ID_1, LocalDateTime.of(2026, 9, 1, 10, 0));
        insertLedger(LEDGER_ID_2, LocalDateTime.of(2026, 9, 2, 10, 0));

        insertAudit(auditId1, "USER_CREATE", LocalDateTime.of(2026, 9, 1, 11, 0));
        insertAudit(auditId2, "USER_STATUS_CHANGE", LocalDateTime.of(2026, 9, 2, 11, 0));
    }

    @Test
    void growthPointQueryKeepsTimeUpperBoundAndExclusiveCursor() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 2, 0, 0);
        LocalDateTime endedAt = LocalDateTime.of(2026, 9, 2, 23, 59, 59);

        assertThat(growthPointMapper.findUpperBound(STUDENT_ID, startedAt, endedAt))
                .isEqualTo(LEDGER_ID_2);
        assertThat(growthPointMapper.count(STUDENT_ID, startedAt, endedAt, LEDGER_ID_2))
                .isEqualTo(1L);
        assertThat(growthPointMapper.findAfter(
                STUDENT_ID, startedAt, endedAt, LEDGER_ID_2, LEDGER_ID_1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(LEDGER_ID_2);
                    assertThat(row.studentName()).isEqualTo("小灵");
                    assertThat(row.taskTitle()).isEqualTo("晨读");
                    assertThat(row.reviewerName()).isEqualTo("王家长");
                });
    }

    @Test
    void iamQuerySupportsNoFilterAndCombinedEventFilter() {
        assertThat(iamAuditMapper.findUpperBound(null, null, null)).isEqualTo(auditId2);
        assertThat(iamAuditMapper.count(null, null, null, auditId2)).isGreaterThanOrEqualTo(2L);

        LocalDateTime startedAt = LocalDateTime.of(2026, 9, 2, 0, 0);
        assertThat(iamAuditMapper.findAfter(
                startedAt, null, IamChangeAuditEventType.USER_STATUS_CHANGE,
                auditId2, auditId1, 10))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(auditId2);
                    assertThat(row.targetName()).isEqualTo("李同学");
                    assertThat(row.operatorName()).isEqualTo("王家长");
                });
    }

    private void insertLedger(long id, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                insert into growth_point_ledger
                    (id, account_id, student_id, source_assignment_id, source_type,
                     change_type, amount, available_delta, reviewer_user_id, occurred_at,
                     source_task_id, base_points_snapshot, decay_percent, streak_days, remark)
                values (?, ?, ?, ?, 'FAMILY', 'TASK_REWARD', 20, 20, ?, ?, ?, 20, 0, 1, '按时完成')
                """, id, ACCOUNT_ID, STUDENT_ID, ASSIGNMENT_ID, PARENT_ID, occurredAt, TASK_ID);
    }

    private void insertAudit(long id, String eventType, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                insert into sys_iam_change_audit
                    (id, event_type, operator_id, target_type, target_id,
                     before_value, after_value, occurred_at)
                values (?, ?, ?, 'USER', ?, 'ENABLED', 'DISABLED', ?)
                """, id, eventType, PARENT_ID, TARGET_USER_ID, occurredAt);
    }
}
