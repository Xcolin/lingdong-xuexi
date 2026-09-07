package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 H2、Flyway 和 MyBatis 验证学生账号注销的完整持久化路径。 */
@SpringBootTest
@ActiveProfiles("test")
class StudentAccountCancellationPersistenceTest {
    private static final long ORGANIZATION_ID = 8930000000000000101L;
    private static final long OPERATOR_ID = 8930000000000000102L;
    private static final long STUDENT_USER_ID = 8930000000000000103L;
    private static final long STUDENT_ID = 8930000000000000104L;
    private static final long ORGANIZATION_ADMIN_ID = 8930000000000000105L;
    private static final long OPERATOR_ROLE_ID = 8930000000000000106L;
    private static final long STUDENT_ROLE_ID = 8930000000000000107L;
    private static final long ENROLLMENT_ID = 8930000000000000108L;
    private static final long CREDENTIAL_ID = 8930000000000000109L;
    private static final long QR_TICKET_ID = 8930000000000000110L;
    private static final long SESSION_ID = 8930000000000000111L;
    private static final long BUILT_IN_ORG_ADMIN_ROLE_ID = 1874244142494646275L;
    private static final long BUILT_IN_STUDENT_ROLE_ID = 1874244142494646278L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StudentAccountCancellationService service;

    @BeforeEach
    void setUp() {
        cleanFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_organization (
                    id, organization_code, organization_name, organization_type,
                    organization_path, sort_order, status
                ) VALUES (?, 'CANCEL_SCHOOL', '原注销学校', 'SCHOOL',
                    '/CANCEL_SCHOOL/', 10, 'ENABLED')
                """, ORGANIZATION_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, username, display_name, password_hash, user_type, status
                ) VALUES (?, 'student_cancel_operator', '注销管理员',
                    'password-hash', 'ORGANIZATION', 'ENABLED')
                """, OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, username, display_name, password_hash, user_type, status
                ) VALUES (?, '20260001', '待注销学生',
                    'student-password-hash', 'STUDENT', 'ENABLED')
                """, STUDENT_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (
                    id, user_id, role_id, organization_id, organization_scope_key
                ) VALUES (?, ?, ?, ?, ?)
                """, OPERATOR_ROLE_ID, OPERATOR_ID, BUILT_IN_ORG_ADMIN_ROLE_ID,
                ORGANIZATION_ID, "ORG:" + ORGANIZATION_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, organization_scope_key)
                VALUES (?, ?, ?, 'GLOBAL')
                """, STUDENT_ROLE_ID, STUDENT_USER_ID, BUILT_IN_STUDENT_ROLE_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_organization_admin (id, organization_id, user_id)
                VALUES (?, ?, ?)
                """, ORGANIZATION_ADMIN_ID, ORGANIZATION_ID, OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_student (
                    id, student_name, grade_code, student_user_id, status
                ) VALUES (?, '待注销学生', 'GRADE_4', ?, 'ENABLED')
                """, STUDENT_ID, STUDENT_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_student_organization (
                    id, student_id, organization_id, relation_type, status,
                    effective_to, updated_at
                ) VALUES (?, ?, ?, 'ENROLLMENT', 'INACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ENROLLMENT_ID, STUDENT_ID, ORGANIZATION_ID);
        jdbcTemplate.update("""
                INSERT INTO auth_student_credential (
                    id, student_user_id, code_hash, code_salt, key_version
                ) VALUES (?, ?, ?, ?, 'v1')
                """, CREDENTIAL_ID, STUDENT_USER_ID, "a".repeat(64), "b".repeat(64));
        jdbcTemplate.update("""
                INSERT INTO auth_student_qr_ticket (
                    id, student_id, student_user_id, token_hash, status,
                    expires_at, issued_by_user_id
                ) VALUES (?, ?, ?, ?, 'ACTIVE', DATEADD('DAY', 1, CURRENT_TIMESTAMP), ?)
                """, QR_TICKET_ID, STUDENT_ID, STUDENT_USER_ID, "c".repeat(64), OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO auth_device_session (
                    id, user_id, client_type, device_id, device_name,
                    access_token_hash, refresh_token_hash, access_expires_at,
                    refresh_expires_at, status, last_active_at
                ) VALUES (?, ?, 'MINIAPP', 'student-device', '学生设备', ?, ?,
                    DATEADD('HOUR', 1, CURRENT_TIMESTAMP),
                    DATEADD('DAY', 1, CURRENT_TIMESTAMP), 'ACTIVE', CURRENT_TIMESTAMP)
                """, SESSION_ID, STUDENT_USER_ID, "d".repeat(64), "e".repeat(64));
        jdbcTemplate.update("""
                UPDATE sys_feature_toggle SET status = 'ENABLED'
                WHERE feature_code = 'STUDENT_ACCOUNT_CANCELLATION'
                """);
    }

    @AfterEach
    void tearDown() {
        cleanFixture();
    }

    @Test
    void anonymizesIdentityAndRevokesEveryStudentCredentialWhileKeepingHistory() {
        AuthenticatedUser operator = new AuthenticatedUser(
                OPERATOR_ID, 1L, "student_cancel_operator", "注销管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));

        assertThat(service.listCandidates(operator))
                .containsExactly(new StudentAccountCancellationCandidate(
                        STUDENT_ID, "待注销学生", "20260001",
                        ORGANIZATION_ID, "原注销学校"));

        service.cancel(operator, new StudentAccountCancellationCommand(
                STUDENT_ID, "学生已经退学并解除全部家长关系", "确认注销学生账号"));

        assertThat(jdbcTemplate.queryForMap(
                "SELECT student_name, status FROM edu_student WHERE id = ?", STUDENT_ID))
                .containsEntry("STUDENT_NAME", "已注销学生")
                .containsEntry("STATUS", "CANCELLED");
        assertThat(jdbcTemplate.queryForMap(
                "SELECT username, display_name, password_hash, status FROM sys_user WHERE id = ?",
                STUDENT_USER_ID))
                .containsEntry("USERNAME", "cancelled_student_" + STUDENT_ID)
                .containsEntry("DISPLAY_NAME", "已注销学生")
                .containsEntry("PASSWORD_HASH", null)
                .containsEntry("STATUS", "CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_student_credential WHERE student_user_id = ?",
                Integer.class, STUDENT_USER_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM auth_student_qr_ticket WHERE id = ?",
                String.class, QR_TICKET_ID)).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM auth_device_session WHERE id = ?",
                String.class, SESSION_ID)).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM edu_student_organization WHERE id = ?",
                String.class, ENROLLMENT_ID)).isEqualTo("INACTIVE");

        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT id, student_id, student_user_id, organization_id,
                       operator_user_id, reason, client_type
                FROM auth_student_account_cancellation WHERE student_id = ?
                """, STUDENT_ID);
        assertThat(String.valueOf(audit.get("ID"))).hasSize(19);
        assertThat(audit)
                .containsEntry("STUDENT_ID", STUDENT_ID)
                .containsEntry("STUDENT_USER_ID", STUDENT_USER_ID)
                .containsEntry("ORGANIZATION_ID", ORGANIZATION_ID)
                .containsEntry("OPERATOR_USER_ID", OPERATOR_ID)
                .containsEntry("REASON", "学生已经退学并解除全部家长关系")
                .containsEntry("CLIENT_TYPE", "WEB");
        assertThat(audit.toString()).doesNotContain("待注销学生", "20260001");
    }

    @Test
    void hidesCandidatesWhenOrganizationIsEffectivelyDisabled() {
        AuthenticatedUser operator = new AuthenticatedUser(
                OPERATOR_ID, 1L, "student_cancel_operator", "注销管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));
        jdbcTemplate.update(
                "UPDATE sys_organization SET effective_status = 'DISABLED' WHERE id = ?",
                ORGANIZATION_ID);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM sys_organization WHERE id = ?", String.class, ORGANIZATION_ID))
                .isEqualTo("ENABLED");
        assertThat(service.listCandidates(operator)).isEmpty();
    }

    private void cleanFixture() {
        jdbcTemplate.update("""
                UPDATE sys_feature_toggle SET status = 'DISABLED'
                WHERE feature_code = 'STUDENT_ACCOUNT_CANCELLATION'
                """);
        jdbcTemplate.update(
                "DELETE FROM auth_student_account_cancellation WHERE student_id = ?", STUDENT_ID);
        jdbcTemplate.update("DELETE FROM auth_device_session WHERE id = ?", SESSION_ID);
        jdbcTemplate.update("DELETE FROM auth_student_qr_ticket WHERE id = ?", QR_TICKET_ID);
        jdbcTemplate.update("DELETE FROM auth_student_credential WHERE id = ?", CREDENTIAL_ID);
        jdbcTemplate.update("DELETE FROM edu_parent_student WHERE student_id = ?", STUDENT_ID);
        jdbcTemplate.update("DELETE FROM edu_student_organization WHERE id = ?", ENROLLMENT_ID);
        jdbcTemplate.update("DELETE FROM edu_student WHERE id = ?", STUDENT_ID);
        jdbcTemplate.update("DELETE FROM sys_organization_admin WHERE id = ?", ORGANIZATION_ADMIN_ID);
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE id IN (?, ?)", OPERATOR_ROLE_ID, STUDENT_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", OPERATOR_ID, STUDENT_USER_ID);
        jdbcTemplate.update("DELETE FROM sys_organization WHERE id = ?", ORGANIZATION_ID);
    }
}
