package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.memory.FixedTestParentSmsCodeGenerator;
import com.lingdong.learning.auth.infrastructure.memory.InMemoryParentSmsVerificationStore;
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

/** 通过真实 H2、Flyway 和 MyBatis 验证机构人工换绑的完整持久化路径。 */
@SpringBootTest
@ActiveProfiles("test")
class ParentMobileManualRecoveryPersistenceTest {
    private static final long ORGANIZATION_ID = 8920000000000000101L;
    private static final long OPERATOR_ID = 8920000000000000102L;
    private static final long PARENT_ID = 8920000000000000103L;
    private static final long STUDENT_ID = 8920000000000000104L;
    private static final long ORGANIZATION_ADMIN_ID = 8920000000000000105L;
    private static final long OPERATOR_ROLE_ID = 8920000000000000106L;
    private static final long PARENT_ROLE_ASSIGNMENT_ID = 8920000000000000107L;
    private static final long ENROLLMENT_ID = 8920000000000000108L;
    private static final long RELATIONSHIP_ID = 8920000000000000109L;
    private static final long ORG_ADMIN_ROLE_ID = 1874244142494646275L;
    private static final long PARENT_ROLE_ID = 1874244142494646277L;
    private static final String OLD_MOBILE = "13800138201";
    private static final String NEW_MOBILE = "13900139201";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ParentMobileManualRecoveryService service;
    @Autowired private InMemoryParentSmsVerificationStore smsStore;

    @BeforeEach
    void setUp() {
        cleanFixture();
        smsStore.clear();
        jdbcTemplate.update("""
                INSERT INTO sys_organization (
                    id, organization_code, organization_name, organization_type,
                    organization_path, sort_order, status
                ) VALUES (?, 'RECOVERY_SCHOOL', '换号核验学校', 'SCHOOL',
                    '/RECOVERY_SCHOOL/', 10, 'ENABLED')
                """, ORGANIZATION_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, username, display_name, mobile, password_hash, user_type, status
                ) VALUES (?, 'recovery_operator', '核验管理员', NULL,
                    'password-hash', 'ORGANIZATION', 'ENABLED')
                """, OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user (
                    id, username, display_name, mobile, password_hash, user_type, status
                ) VALUES (?, ?, '待换号家长', ?, 'password-hash', 'FAMILY', 'ENABLED')
                """, PARENT_ID, OLD_MOBILE, OLD_MOBILE);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (
                    id, user_id, role_id, organization_id, organization_scope_key
                ) VALUES (?, ?, ?, ?, ?)
                """, OPERATOR_ROLE_ID, OPERATOR_ID, ORG_ADMIN_ROLE_ID,
                ORGANIZATION_ID, "ORG:" + ORGANIZATION_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (id, user_id, role_id, organization_scope_key)
                VALUES (?, ?, ?, 'GLOBAL')
                """, PARENT_ROLE_ASSIGNMENT_ID, PARENT_ID, PARENT_ROLE_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_organization_admin (id, organization_id, user_id)
                VALUES (?, ?, ?)
                """, ORGANIZATION_ADMIN_ID, ORGANIZATION_ID, OPERATOR_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_student (id, student_name, grade_code, status)
                VALUES (?, '核验关联学生', 'GRADE_4', 'ENABLED')
                """, STUDENT_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_student_organization (
                    id, student_id, organization_id, relation_type, status
                ) VALUES (?, ?, ?, 'ENROLLMENT', 'ACTIVE')
                """, ENROLLMENT_ID, STUDENT_ID, ORGANIZATION_ID);
        jdbcTemplate.update("""
                INSERT INTO edu_parent_student (
                    id, parent_user_id, student_id, relation_role,
                    status, primary_scope_key
                ) VALUES (?, ?, ?, 'PRIMARY_GUARDIAN', 'ACTIVE', 'PRIMARY')
                """, RELATIONSHIP_ID, PARENT_ID, STUDENT_ID);
        jdbcTemplate.update("""
                UPDATE sys_feature_toggle SET status = 'ENABLED'
                WHERE feature_code = 'PARENT_MOBILE_MANUAL_RECOVERY'
                """);
    }

    @AfterEach
    void tearDown() {
        smsStore.clear();
        cleanFixture();
    }

    @Test
    void changesMobileAndKeepsOnlyDigestAuditInsideOrganizationScope() {
        AuthenticatedUser operator = new AuthenticatedUser(
                OPERATOR_ID, 1L, "recovery_operator", "核验管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));

        assertThat(service.listCandidates(operator))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(candidate.parentUserId()).isEqualTo(PARENT_ID);
                    assertThat(candidate.organizationId()).isEqualTo(ORGANIZATION_ID);
                    assertThat(candidate.maskedMobile()).isEqualTo("138****8201");
                });

        service.issueCode(operator, new IssueParentMobileManualRecoveryCodeCommand(
                STUDENT_ID, PARENT_ID, NEW_MOBILE, "persistence-source"));
        service.recover(operator, new ParentMobileManualRecoveryCommand(
                STUDENT_ID, PARENT_ID, NEW_MOBILE, FixedTestParentSmsCodeGenerator.CODE,
                "家长到校并完成身份核验", "已完成线下身份核验"));

        assertThat(jdbcTemplate.queryForMap(
                "SELECT username, mobile FROM sys_user WHERE id = ?", PARENT_ID))
                .containsEntry("USERNAME", NEW_MOBILE)
                .containsEntry("MOBILE", NEW_MOBILE);
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT id, parent_user_id, student_id, organization_id, operator_user_id,
                       old_mobile_digest, new_mobile_digest, reason, client_type
                FROM auth_parent_mobile_manual_recovery WHERE parent_user_id = ?
                """, PARENT_ID);
        assertThat(String.valueOf(audit.get("ID"))).hasSize(19);
        assertThat(audit)
                .containsEntry("PARENT_USER_ID", PARENT_ID)
                .containsEntry("STUDENT_ID", STUDENT_ID)
                .containsEntry("ORGANIZATION_ID", ORGANIZATION_ID)
                .containsEntry("OPERATOR_USER_ID", OPERATOR_ID)
                .containsEntry("REASON", "家长到校并完成身份核验")
                .containsEntry("CLIENT_TYPE", "WEB");
        assertThat(audit.get("OLD_MOBILE_DIGEST").toString())
                .hasSize(64)
                .doesNotContain(OLD_MOBILE);
        assertThat(audit.get("NEW_MOBILE_DIGEST").toString())
                .hasSize(64)
                .doesNotContain(NEW_MOBILE);
    }

    @Test
    void hidesCandidatesWhenOrganizationIsEffectivelyDisabled() {
        AuthenticatedUser operator = new AuthenticatedUser(
                OPERATOR_ID, 1L, "recovery_operator", "核验管理员",
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
                WHERE feature_code = 'PARENT_MOBILE_MANUAL_RECOVERY'
                """);
        jdbcTemplate.update(
                "DELETE FROM auth_parent_mobile_manual_recovery WHERE parent_user_id = ?", PARENT_ID);
        jdbcTemplate.update("DELETE FROM edu_parent_student WHERE id = ?", RELATIONSHIP_ID);
        jdbcTemplate.update("DELETE FROM edu_student_organization WHERE id = ?", ENROLLMENT_ID);
        jdbcTemplate.update("DELETE FROM edu_student WHERE id = ?", STUDENT_ID);
        jdbcTemplate.update("DELETE FROM sys_organization_admin WHERE id = ?", ORGANIZATION_ADMIN_ID);
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE id IN (?, ?)", OPERATOR_ROLE_ID, PARENT_ROLE_ASSIGNMENT_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (?, ?)", OPERATOR_ID, PARENT_ID);
        jdbcTemplate.update("DELETE FROM sys_organization WHERE id = ?", ORGANIZATION_ID);
    }
}
