package com.lingdong.learning.studentimport.infrastructure.persistence;

import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 Flyway、H2 和 MyBatis 验证学员导入状态与一次性凭证条件更新。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StudentImportPersistenceTest {
    private static final long USER_ID = 1874244142494647011L;
    private static final long SCHOOL_ID = 1874244142494647012L;
    private static final long SOURCE_FILE_ID = 1874244142494647013L;
    private static final long TEMPLATE_ID = 1874244142494647014L;
    private static final long VALIDATION_JOB_ID = 1874244142494647015L;
    private static final long EXECUTION_ID = 1874244142494647016L;
    private static final long ROW_ID = 1874244142494647017L;
    private static final long CREDENTIAL_FILE_ID = 1874244142494647018L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StudentImportExecutionMapper executionMapper;
    @Autowired private StudentImportRowMapper rowMapper;

    @Test
    void persistsClaimsRowsAndConsumesCredentialOnlyOnce() {
        insertFixtures();
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 16, 30);
        StudentImportExecutionRecord execution = queuedExecution(now);
        StudentImportRowRecord row = new StudentImportRowRecord(
                ROW_ID, EXECUTION_ID, 2, StudentImportRowStatus.PENDING,
                null, null, null, null, null, null, null, 0, null, null);

        assertThat(executionMapper.insert(execution)).isEqualTo(1);
        assertThat(rowMapper.insertBatch(List.of(row))).isEqualTo(1);
        assertThat(executionMapper.findByValidationJobId(VALIDATION_JOB_ID).id())
                .isEqualTo(EXECUTION_ID);
        assertThat(executionMapper.findQueued(10)).extracting(StudentImportExecutionRecord::id)
                .containsExactly(EXECUTION_ID);

        assertThat(executionMapper.claim(EXECUTION_ID, 0L)).isEqualTo(1);
        assertThat(executionMapper.claim(EXECUTION_ID, 0L)).isZero();
        assertThat(rowMapper.findByExecutionIdAndStatus(
                EXECUTION_ID, StudentImportRowStatus.PENDING, 0, 20))
                .extracting(StudentImportRowRecord::rowNumber).containsExactly(2);

        byte[] cipher = new byte[] {1, 2, 3};
        byte[] nonce = new byte[] {4, 5, 6};
        assertThat(rowMapper.markSucceeded(
                ROW_ID, 1874244142494647019L, "12345678", cipher, nonce, "v1"))
                .isEqualTo(1);
        assertThat(rowMapper.markSucceeded(
                ROW_ID, 1874244142494647020L, "87654321", cipher, nonce, "v1"))
                .isZero();

        insertCredentialFile();
        LocalDateTime expiresAt = now.plusHours(24);
        assertThat(executionMapper.complete(
                EXECUTION_ID, 1L, StudentImportExecutionStatus.SUCCEEDED,
                1, 1, 0, CREDENTIAL_FILE_ID, expiresAt, now.plusMinutes(1)))
                .isEqualTo(1);
        assertThat(executionMapper.consumeCredential(
                EXECUTION_ID, 2L, now.plusHours(1), now.plusHours(1))).isEqualTo(1);
        assertThat(executionMapper.consumeCredential(
                EXECUTION_ID, 3L, now.plusHours(1), now.plusHours(1))).isZero();

        StudentImportExecutionRecord consumed = executionMapper.findById(EXECUTION_ID);
        assertThat(consumed.credentialStatus()).isEqualTo(StudentImportCredentialStatus.CONSUMED);
        assertThat(rowMapper.clearCredentials(EXECUTION_ID)).isEqualTo(1);
        assertThat(rowMapper.findByExecutionIdAndStatus(
                EXECUTION_ID, StudentImportRowStatus.SUCCEEDED, 0, 20).get(0)
                .credentialCiphertext()).isNull();
    }

    @Test
    void requesterListAndCountApplyCurrentOrganizationScopeBeforePaging() {
        insertFixtures();
        executionMapper.insert(queuedExecution(LocalDateTime.now()));
        var own = new com.lingdong.learning.datascope.application.OrganizationDataScope(false, false, java.util.List.of("/SCHOOL-IMPORT/"));
        var outside = new com.lingdong.learning.datascope.application.OrganizationDataScope(false, false, java.util.List.of("/OUTSIDE/"));
        var all = com.lingdong.learning.datascope.application.OrganizationDataScope.all(false);
        assertThat(executionMapper.findPageByRequesterScope(USER_ID, null, own, 0, 20)).hasSize(1);
        assertThat(executionMapper.countByRequesterScope(USER_ID, null, own)).isEqualTo(1);
        assertThat(executionMapper.findPageByRequesterScope(USER_ID, null, all, 0, 20)).hasSize(1);
        assertThat(executionMapper.findPageByRequesterScope(USER_ID, null, outside, 0, 20)).isEmpty();
        assertThat(executionMapper.countByRequesterScope(USER_ID, null, outside)).isZero();
        assertThat(executionMapper.countByRequesterScope(USER_ID + 1, null, all)).isZero();
    }

    private StudentImportExecutionRecord queuedExecution(LocalDateTime now) {
        return new StudentImportExecutionRecord(
                EXECUTION_ID, "SIM-" + EXECUTION_ID, VALIDATION_JOB_ID,
                USER_ID, SCHOOL_ID, null, StudentImportExecutionStatus.QUEUED,
                0L, 1, 0, 0, 0, null, null, null,
                StudentImportCredentialStatus.NONE, null, null,
                now, null, null, now, now);
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'student_import_admin', '导入机构管理员', 'ORGANIZATION', 'ENABLED')
                """, USER_ID);
        jdbcTemplate.update("""
                insert into sys_organization (
                    id, parent_id, parent_scope_key, organization_code, organization_name,
                    organization_type, organization_path, sort_order, status, effective_status, version_no
                ) values (?, null, 'ROOT', 'SCHOOL-IMPORT', '导入测试学校',
                    'SCHOOL', '/SCHOOL-IMPORT/', 1, 'ENABLED', 'ENABLED', 1)
                """, SCHOOL_ID);
        jdbcTemplate.update("""
                insert into sys_file (
                    id, storage_key, original_name, extension, content_type,
                    size_bytes, uploader_id, status, uploaded_at
                ) values (?, 'student-import/source', 'students.xlsx', 'xlsx',
                    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                    128, ?, 'AVAILABLE', current_timestamp)
                """, SOURCE_FILE_ID, USER_ID);
        jdbcTemplate.update("""
                insert into sys_import_export_template (
                    id, template_name, template_type, module_code, version, file_id,
                    is_default, default_scope_key, status, version_no
                ) values (?, '学员导入', 'IMPORT', 'STUDENT', 'V1', ?, 1, 'DEFAULT', 'ENABLED', 0)
                """, TEMPLATE_ID, SOURCE_FILE_ID);
        jdbcTemplate.update("""
                insert into sys_import_job (
                    id, job_code, template_id, template_version, template_name,
                    field_mapping_snapshot, source_file_id, requester_id, organization_id,
                    status, version_no, total_rows, processed_rows, valid_rows, invalid_rows, queued_at
                ) values (?, 'IMP-STUDENT-V58', ?, 'V1', '学员导入', '[]', ?, ?, ?,
                    'VALIDATED', 1, 1, 1, 1, 0, current_timestamp)
                """, VALIDATION_JOB_ID, TEMPLATE_ID, SOURCE_FILE_ID, USER_ID, SCHOOL_ID);
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, status)
                values (?, '持久化测试学员', 'ENABLED')
                """, 1874244142494647019L);
    }

    private void insertCredentialFile() {
        jdbcTemplate.update("""
                insert into sys_file (
                    id, storage_key, original_name, extension, content_type,
                    size_bytes, uploader_id, status, uploaded_at
                ) values (?, 'student-import/credential', 'credential.enc', 'enc',
                    'application/octet-stream', 128, ?, 'AVAILABLE', current_timestamp)
                """, CREDENTIAL_FILE_ID, USER_ID);
    }
}
