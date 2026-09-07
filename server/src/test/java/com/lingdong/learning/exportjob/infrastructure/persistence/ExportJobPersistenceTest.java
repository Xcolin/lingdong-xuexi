package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.exportjob.domain.ExportJobEventRecord;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 Flyway、H2 和 MyBatis 验证导出作业状态持久化。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExportJobPersistenceTest {
    private static final long USER_ID = 1874244142494646901L;
    private static final long FILE_ID = 1874244142494646902L;
    private static final long TEMPLATE_ID = 1874244142494646903L;
    private static final long JOB_ID = 1874244142494646904L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ExportJobMapper jobMapper;
    @Autowired private ExportJobEventMapper eventMapper;

    @Test
    void persistsClaimsAndAdvancesOneQueuedJobWithOrderedEvents() {
        insertFixtures();
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 3, 9, 0);
        ExportJobRecord job = queuedJob(requestedAt);

        assertThat(jobMapper.insert(job)).isEqualTo(1);
        assertThat(jobMapper.findQueued(10)).extracting(ExportJobRecord::id).contains(JOB_ID);
        assertThat(jobMapper.findPageByRequester(
                USER_ID, ExportJobType.GROWTH_POINT_LEDGER, ExportJobStatus.QUEUED, 0, 20
        )).extracting(ExportJobRecord::id).containsExactly(JOB_ID);
        assertThat(jobMapper.countByRequester(
                USER_ID, ExportJobType.GROWTH_POINT_LEDGER, ExportJobStatus.QUEUED
        )).isEqualTo(1);

        assertThat(jobMapper.claim(JOB_ID, 0L)).isEqualTo(1);
        assertThat(jobMapper.claim(JOB_ID, 0L)).isZero();
        ExportJobRecord claimed = jobMapper.findById(JOB_ID);
        assertThat(claimed.status()).isEqualTo(ExportJobStatus.EXPORTING);
        assertThat(claimed.versionNo()).isEqualTo(1L);

        assertThat(jobMapper.updateProgress(JOB_ID, 1L, 50L, 20L)).isEqualTo(1);
        assertThat(jobMapper.updateProgress(JOB_ID, 1L, 50L, 30L)).isZero();
        ExportJobRecord progressed = jobMapper.findById(JOB_ID);
        assertThat(progressed.totalRows()).isEqualTo(50L);
        assertThat(progressed.processedRows()).isEqualTo(20L);
        assertThat(progressed.versionNo()).isEqualTo(2L);

        eventMapper.insert(new ExportJobEventRecord(
                1874244142494646905L, JOB_ID, ExportJobEventType.REQUESTED,
                USER_ID, "已申请普通导出", requestedAt, requestedAt
        ));
        eventMapper.insert(new ExportJobEventRecord(
                1874244142494646906L, JOB_ID, ExportJobEventType.CLAIMED,
                null, "系统已领取导出作业", requestedAt.plusMinutes(1), requestedAt.plusMinutes(1)
        ));

        assertThat(eventMapper.findByJobId(JOB_ID))
                .extracting(ExportJobEventRecord::eventType)
                .containsExactly(ExportJobEventType.REQUESTED, ExportJobEventType.CLAIMED);
    }

    private ExportJobRecord queuedJob(LocalDateTime requestedAt) {
        return new ExportJobRecord(
                JOB_ID, "EXP-" + JOB_ID, ExportJobType.GROWTH_POINT_LEDGER,
                TEMPLATE_ID, "积分明细导出", "V1", USER_ID, null, null,
                "{}", "[]", "{}", "{}", "导出本月积分明细", false,
                ExportJobStatus.QUEUED, 0L, null, 0L, 0L, null, null,
                "0".repeat(64), requestedAt, null, requestedAt, null, null,
                requestedAt, requestedAt
        );
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, ?, ?, 'PARENT', 'ENABLED')
                """, USER_ID, "export_persistence_parent", "导出持久化家长");
        jdbcTemplate.update("""
                insert into sys_file (
                    id, storage_key, original_name, extension, content_type,
                    size_bytes, uploader_id, status, uploaded_at
                ) values (?, ?, 'export-template.xlsx', 'xlsx',
                    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                    128, ?, 'AVAILABLE', current_timestamp)
                """, FILE_ID, "export/persistence/template", USER_ID);
        jdbcTemplate.update("""
                insert into sys_import_export_template (
                    id, template_name, template_type, module_code, version, file_id,
                    is_default, default_scope_key, status, version_no
                ) values (?, '积分明细导出', 'EXPORT', 'REPORT', 'V1', ?, 1, 'DEFAULT', 'ENABLED', 0)
                """, TEMPLATE_ID, FILE_ID);
    }
}
