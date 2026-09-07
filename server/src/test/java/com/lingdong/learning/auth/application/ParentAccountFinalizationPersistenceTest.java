package com.lingdong.learning.auth.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ParentAccountFinalizationPersistenceTest {
    private static final long USER_ID = 8910000000000000101L;
    private static final long STUDENT_ID = 8910000000000000102L;
    private static final long RELATIONSHIP_ID = 8910000000000000103L;
    private static final long USER_ROLE_ID = 8910000000000000104L;
    private static final long WECHAT_BINDING_ID = 8910000000000000105L;
    private static final long SESSION_ID = 8910000000000000106L;
    private static final long CANCELLATION_ID = 8910000000000000107L;
    private static final long PARENT_ROLE_ID = 1874244142494646277L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ParentAccountFinalizationService service;

    @BeforeEach
    void setUp() {
        cleanFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, username, display_name, mobile, password_hash, user_type, status
                ) VALUES (?, '13800138101', '待注销家长', '13800138101', 'password-hash', 'FAMILY', 'ENABLED')
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, organization_scope_key)
                VALUES (?, ?, ?, 'GLOBAL')
                """, USER_ROLE_ID, USER_ID, PARENT_ROLE_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_student (id, student_name, grade_code, status)
                VALUES (?, '历史关系学生', 'GRADE_3', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_parent_student (
                    id, parent_user_id, student_id, relation_role, status,
                    primary_scope_key, bound_at, unbound_at
                ) VALUES (?, ?, ?, 'PRIMARY_GUARDIAN', 'INACTIVE', ?, ?, ?)
                """, RELATIONSHIP_ID, USER_ID, STUDENT_ID, "CLOSED:" + RELATIONSHIP_ID,
                LocalDateTime.of(2025, 1, 1, 9, 0), LocalDateTime.of(2025, 2, 1, 9, 0));
        jdbcTemplate.update("""
                INSERT INTO auth_parent_wechat_binding (
                    id, user_id, app_id, open_id, union_id, status, bound_at
                ) VALUES (?, ?, 'wx-test-app', 'openid-finalization',
                    'unionid-finalization', 'ACTIVE', ?)
                """, WECHAT_BINDING_ID, USER_ID, LocalDateTime.of(2025, 1, 1, 9, 0));
        jdbcTemplate.update("""
                INSERT INTO auth_device_session (
                    id, user_id, client_type, device_id, device_name,
                    access_token_hash, refresh_token_hash, access_expires_at,
                    refresh_expires_at, status, last_active_at
                ) VALUES (?, ?, 'WEB', 'device-finalization', '终结测试设备',
                    ?, ?, ?, ?, 'ACTIVE', ?)
                """, SESSION_ID, USER_ID, "a".repeat(64), "b".repeat(64),
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now().plusDays(7),
                LocalDateTime.now());
        jdbcTemplate.update("""
                INSERT INTO auth_parent_account_cancellation (
                    id, user_id, status, active_scope_key, requested_at, cooling_ends_at,
                    next_finalize_at
                ) VALUES (?, ?, 'COOLING_OFF', 'ACTIVE', ?, ?, ?)
                """, CANCELLATION_ID, USER_ID,
                LocalDateTime.of(2025, 1, 1, 9, 0),
                LocalDateTime.of(2025, 1, 8, 9, 0),
                LocalDateTime.of(2025, 1, 8, 9, 0));
    }

    @AfterEach
    void tearDown() {
        cleanFixture();
    }

    @Test
    void removesDirectIdentityAndKeepsHistoricalRelationshipFact() {
        assertThat(service.finalizeCancellation(CANCELLATION_ID))
                .isEqualTo(ParentAccountFinalizationResult.FINALIZED);

        assertThat(jdbcTemplate.queryForMap("""
                SELECT username, display_name, mobile, password_hash, status
                FROM sys_user WHERE id = ?
                """, USER_ID))
                .containsEntry("USERNAME", "cancelled_" + USER_ID)
                .containsEntry("DISPLAY_NAME", "已注销用户")
                .containsEntry("STATUS", "CANCELLED")
                .containsEntry("MOBILE", null)
                .containsEntry("PASSWORD_HASH", null);
        assertThat(count("auth_parent_wechat_binding", "user_id", USER_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM auth_device_session WHERE id = ?", String.class, SESSION_ID))
                .isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status FROM auth_parent_account_cancellation WHERE id = ?
                """, String.class, CANCELLATION_ID)).isEqualTo("FINALIZED");
        assertThat(count("edu_parent_student", "id", RELATIONSHIP_ID)).isEqualTo(1);
    }

    private int count(String table, String column, long value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }

    private void cleanFixture() {
        jdbcTemplate.update("DELETE FROM auth_parent_account_cancellation WHERE id = ?", CANCELLATION_ID);
        jdbcTemplate.update("DELETE FROM auth_device_session WHERE id = ?", SESSION_ID);
        jdbcTemplate.update("DELETE FROM auth_parent_wechat_binding WHERE id = ?", WECHAT_BINDING_ID);
        jdbcTemplate.update("DELETE FROM edu_parent_student WHERE id = ?", RELATIONSHIP_ID);
        jdbcTemplate.update("DELETE FROM edu_student WHERE id = ?", STUDENT_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE id = ?", USER_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", USER_ID);
    }
}
