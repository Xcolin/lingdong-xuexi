package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.exportjob.application.GrowthReviewExportPayloadStore;
import com.lingdong.learning.growthpoint.application.GrowthReviewDetailView;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService.Prepared;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;
import com.lingdong.learning.growthpoint.infrastructure.pdf.GrowthReviewPdfRenderer.Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 使用本地真实迁移和 SQL 检验内容固化，不代表导出创建入口已开放。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GrowthReviewExportPayloadStoreTest {
    private static final long USER = 1874244142494650101L;
    private static final long STUDENT = 1874244142494650102L;
    private static final long JOB = 1874244142494650103L;
    private static final long FILE = 1874244142494650104L;
    private static final long TEMPLATE = 1874244142494650105L;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private GrowthReviewExportPayloadStore store;
    @Autowired private ExportJobMapper jobs;
    @Autowired private com.lingdong.learning.exportjob.application.GrowthReviewPdfTemplateService pdfTemplates;

    @Test void templateOptionsExcludeRetiredAttachmentsInSql() {
        assertThat(pdfTemplates.findOptions()).extracting(com.lingdong.learning.exportjob.application.GrowthReviewPdfTemplateService.Option::id)
                .contains(String.valueOf(TEMPLATE));
        jdbc.update("update sys_file set status='RETIRED' where id=?", FILE);
        sqlSession.clearCache();
        assertThat(pdfTemplates.findOptions()).extracting(com.lingdong.learning.exportjob.application.GrowthReviewPdfTemplateService.Option::id)
                .doesNotContain(String.valueOf(TEMPLATE));
    }
    @Autowired private com.lingdong.learning.exportjob.application.GrowthReviewExportHistoryService history;
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private com.lingdong.learning.exportjob.application.GrowthReviewExportApplicationService creation;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobClaimService claims;
    @Autowired private com.lingdong.learning.exportjob.application.ExportJobExecutionService execution;
    @Autowired private com.lingdong.learning.attachment.application.ManagedAttachmentContentService contentService;
    @Autowired private com.lingdong.learning.attachment.application.AttachmentContentStorage storage;
    private final String templateKey = "payload/template-" + java.util.UUID.randomUUID();

    @AfterEach void removeTemplateContent() {
        storage.delete(templateKey);
        jdbc.queryForList("select storage_key from sys_file where uploader_id=? and file_category='REPORT_EXPORT'", String.class, USER)
                .forEach(storage::delete);
    }

    @BeforeEach void fixtures() {
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values (?, 'payload_parent', '测试家长', 'FAMILY', 'ENABLED')", USER);
        jdbc.update("insert into edu_student(id,student_name,status) values (?, '测试学生', 'ENABLED')", STUDENT);
        jdbc.update("""
                insert into sys_user_role(id,user_id,role_id,organization_scope_key)
                select ?, ?, id, 'GLOBAL' from sys_role where role_code='PARENT'
                """, USER + 30, USER);
        jdbc.update("""
                insert into edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key)
                values (?, ?, ?, 'SECONDARY_GUARDIAN', 'ACTIVE', 'SECONDARY')
                """, USER + 31, USER, STUDENT);
        jdbc.update("""
                update sys_feature_toggle set status='ENABLED' where scope_key='GLOBAL'
                and feature_code in ('GROWTH_REVIEW_PDF_EXPORT','DATA_EXPORT','IMPORT_EXPORT_TEMPLATE_MANAGEMENT','ATTACHMENT_SERVICE')
                """);
        jdbc.update("""
                insert into sys_file(id,storage_key,original_name,extension,content_type,size_bytes,uploader_id,status,uploaded_at)
                values (?, ?, 'template.json', 'json', 'application/json', 1, ?, 'AVAILABLE', current_timestamp)
                """, FILE, templateKey, USER);
        storage.store(templateKey, "{\"schemaVersion\":1,\"reportType\":\"GROWTH_REVIEW\",\"templates\":[\"SIMPLE\",\"DETAILED\"]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("""
                insert into sys_import_export_template(id,template_name,template_type,module_code,version,file_id,is_default,default_scope_key,status,version_no)
                values (?, '测试模板', 'EXPORT', 'REPORT', 'V1', ?, 0, 'PAYLOAD_TEST', 'ENABLED', 0)
                """, TEMPLATE, FILE);
        jdbc.update("""
                insert into sys_export_job(id,job_code,export_type,template_id,template_name,template_version,requester_id,student_id,
                  filter_snapshot,column_snapshot,scope_snapshot,mask_policy_snapshot,request_reason,status,request_source_hash,requested_at)
                values (?, ?, 'GROWTH_REVIEW_PDF', ?, '测试模板', 'V1', ?, ?, '{}', '[]', '{}', '{}', '测试内容存储', 'QUEUED', ?, current_timestamp)
                """, JOB, "EXP-" + JOB, TEMPLATE, USER, STUDENT, "0".repeat(64));
    }

    @Test void roundTripsFrozenContentAndNineteenDigitIdentity() {
        var prepared = prepared(STUDENT);
        store.freeze(JOB, prepared, Template.DETAILED);
        var restored = store.load(jobs.findById(JOB));
        assertThat(restored.prepared()).isEqualTo(prepared);
        assertThat(restored.template()).isEqualTo(Template.DETAILED);
        assertThat(jdbc.queryForObject("select id from sys_export_job_payload where job_id=?", Long.class, JOB).toString()).hasSize(19);
        assertThatThrownBy(() -> restored.prepared().reports().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void claimsRendersStoresAndCompletesPdfWithRealServices() throws Exception {
        store.freeze(JOB, prepared(STUDENT), Template.DETAILED);
        var claimed = claims.claim(JOB, 0L);
        assertThat(execution.execute(claimed)).isTrue();
        var completed = jobs.findById(JOB);
        assertThat(completed.status()).isEqualTo(com.lingdong.learning.exportjob.domain.ExportJobStatus.SUCCEEDED);
        assertThat(completed.resultFileId().toString()).hasSize(19);
        assertThat(completed.totalRows()).isEqualTo(1L);
        assertThat(completed.processedRows()).isEqualTo(1L);
        var file = contentService.read(completed.resultFileId());
        assertThat(file.contentType()).isEqualTo("application/pdf");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(file.content())) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("测试学生", "2026-09-01");
        }
        assertThat(jdbc.queryForObject("select count(*) from sys_file_relation where business_id=? and relation_type='EXPORT_JOB_RESULT' and status='ACTIVE'",
                Integer.class, JOB)).isEqualTo(1);
    }

    @Test void createsQueuedJobAndFrozenContentFromRealReviewThenExecutes() {
        long reviewId = USER + 50;
        long snapshotId = USER + 51;
        var day = LocalDate.of(2026, 9, 1);
        jdbc.update("""
                insert into growth_review(id,student_id,period_type,period_start,period_end,status,created_at,updated_at)
                values (?, ?, 'DAY', ?, ?, 'FINAL', current_timestamp, current_timestamp)
                """, reviewId, STUDENT, day, day);
        jdbc.update("""
                insert into growth_review_snapshot(id,review_id,content_version,task_total_count,completed_count,
                  in_progress_count,pending_optimization_count,exempted_count,completion_rate,earned_points,pause_count,
                  generation_source,fact_fingerprint,data_cutoff_at,generated_at)
                values (?, ?, 1, 1, 1, 0, 0, 0, 1, 10, 0, 'AUTO', ?, current_timestamp, current_timestamp)
                """, snapshotId, reviewId, "a".repeat(64));
        jdbc.update("update growth_review set current_snapshot_id=? where id=?", snapshotId, reviewId);
        var user = new com.lingdong.learning.auth.application.AuthenticatedUser(USER, USER + 60, "payload_parent", "测试家长",
                com.lingdong.learning.auth.domain.AuthClientType.WEB, List.of("PARENT"));
        var created = creation.create(user, new com.lingdong.learning.growthpoint.application.GrowthReviewExportSelection(
                STUDENT, reviewId, null, null, null), TEMPLATE, Template.DETAILED, "  导出日报  ", "127.0.0.1");
        assertThat(created.status()).isEqualTo(com.lingdong.learning.exportjob.domain.ExportJobStatus.QUEUED);
        assertThat(created.id().toString()).hasSize(19);
        assertThat(created.requestReason()).isEqualTo("导出日报");
        assertThat(created.requestSourceHash()).hasSize(64).doesNotContain("127.0.0.1");
        assertThat(store.load(created).prepared().reports().get(0).snapshotId()).isEqualTo(snapshotId);
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job_event where job_id=? and event_type='REQUESTED'",
                Integer.class, created.id())).isEqualTo(1);
        assertThat(execution.execute(claims.claim(created.id(), 0L))).isTrue();
        assertThat(jobs.findById(created.id()).status()).isEqualTo(com.lingdong.learning.exportjob.domain.ExportJobStatus.SUCCEEDED);
    }

    @Test void rejectsInvalidCreateParametersBeforeWriting() {
        var user = new com.lingdong.learning.auth.application.AuthenticatedUser(USER, USER + 60, "payload_parent", "测试家长",
                com.lingdong.learning.auth.domain.AuthClientType.WEB, List.of("PARENT"));
        var selection = new com.lingdong.learning.growthpoint.application.GrowthReviewExportSelection(STUDENT, USER + 50, null, null, null);
        assertThatThrownBy(() -> creation.create(user, selection, TEMPLATE, Template.SIMPLE, " ", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> creation.create(user, selection, null, Template.SIMPLE, "导出", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job where requester_id=?", Integer.class, USER)).isEqualTo(1);
    }

    @Test void listsAndDownloadsHistoricalPdfAfterGenerationFeaturesAreDisabled() {
        completePdf();
        jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code in ('GROWTH_REVIEW_PDF_EXPORT','DATA_EXPORT','IMPORT_EXPORT_TEMPLATE_MANAGEMENT')");
        sqlSession.clearCache();
        var page = history.findPage(parent(), STUDENT, null, 1, 20);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(com.lingdong.learning.exportjob.application.ExportJobView::id).containsExactly(JOB);
        assertThat(history.findDetail(parent(), JOB).status()).isEqualTo(com.lingdong.learning.exportjob.domain.ExportJobStatus.SUCCEEDED);
        assertThat(history.download(parent(), JOB).contentType()).isEqualTo("application/pdf");
    }

    @Test void isolatesStudentRequesterAndStatusBeforePagination() {
        long otherStudent = STUDENT + 100;
        long otherUser = USER + 200;
        jdbc.update("insert into edu_student(id,student_name,status) values (?, '其他学生', 'ENABLED')", otherStudent);
        jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values (?, 'history_other', '其他家长', 'FAMILY', 'ENABLED')", otherUser);
        for (int i = 1; i <= 3; i++) {
            long id = JOB + 300 + i;
            jdbc.update("""
                    insert into sys_export_job(id,job_code,export_type,template_id,template_name,template_version,requester_id,student_id,
                      filter_snapshot,column_snapshot,scope_snapshot,mask_policy_snapshot,request_reason,status,request_source_hash,requested_at)
                    values (?, ?, 'GROWTH_REVIEW_PDF', ?, '测试模板', 'V1', ?, ?, '{}', '[]', '{}', '{}', '历史隔离', 'QUEUED', ?, current_timestamp)
                    """, id, "EXP-" + id, TEMPLATE, i == 2 ? otherUser : USER, i == 1 ? otherStudent : STUDENT, "0".repeat(64));
        }
        var first = history.findPage(parent(), STUDENT, null, 1, 1);
        var second = history.findPage(parent(), STUDENT, null, 2, 1);
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.items()).extracting(com.lingdong.learning.exportjob.application.ExportJobView::id).containsExactly(JOB + 303);
        assertThat(second.items()).extracting(com.lingdong.learning.exportjob.application.ExportJobView::id).containsExactly(JOB);
        assertThat(history.findPage(parent(), STUDENT, com.lingdong.learning.exportjob.domain.ExportJobStatus.SUCCEEDED, 1, 20).total()).isZero();
    }

    @Test void refusesRetiredResultFile() {
        completePdf();
        jdbc.update("update sys_file set status='RETIRED' where id=(select result_file_id from sys_export_job where id=?)", JOB);
        sqlSession.clearCache();
        assertThatThrownBy(() -> history.download(parent(), JOB)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
    }

    @Test void refusesHistoricalAccessAfterRelationshipRevocation() {
        completePdf();
        jdbc.update("delete from edu_parent_student where parent_user_id=? and student_id=?", USER, STUDENT);
        sqlSession.clearCache();
        assertThatThrownBy(() -> history.download(parent(), JOB)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> history.findPage(parent(), STUDENT, null, 1, 20)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
    }

    @Test void refusesDownloadWhenBusinessRelationIsReleasedOrVisibilityChanges() {
        completePdf();
        jdbc.update("update sys_file_relation set visible_scope='SYSTEM_CONFIGURATION' where business_id=?", JOB);
        sqlSession.clearCache();
        assertThatThrownBy(() -> history.download(parent(), JOB)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        jdbc.update("update sys_file_relation set visible_scope='BUSINESS_AUTHORIZED',status='RELEASED' where business_id=?", JOB);
        sqlSession.clearCache();
        assertThatThrownBy(() -> history.download(parent(), JOB)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
    }

    @Test void refusesIncompleteResultDifferentOwnerAndInvalidPagination() {
        assertThatThrownBy(() -> history.download(parent(), JOB)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        var other = new com.lingdong.learning.auth.application.AuthenticatedUser(USER + 999, USER + 998, "other", "其他家长",
                com.lingdong.learning.auth.domain.AuthClientType.WEB, List.of("PARENT"));
        assertThatThrownBy(() -> history.findDetail(other, JOB)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> history.findPage(parent(), STUDENT, null, 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    private void completePdf() {
        store.freeze(JOB, prepared(STUDENT), Template.SIMPLE);
        assertThat(execution.execute(claims.claim(JOB, 0L))).isTrue();
    }

    private com.lingdong.learning.auth.application.AuthenticatedUser parent() {
        return new com.lingdong.learning.auth.application.AuthenticatedUser(USER, USER + 60, "payload_parent", "测试家长",
                com.lingdong.learning.auth.domain.AuthClientType.WEB, List.of("PARENT"));
    }

    @Test void refusesPayloadWriteWhenPdfGenerationFeatureIsDisabled() {
        jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code='GROWTH_REVIEW_PDF_EXPORT'");
        assertThatThrownBy(() -> store.freeze(JOB, prepared(STUDENT), Template.SIMPLE))
                .isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job_payload where job_id=?", Integer.class, JOB)).isZero();
    }

    @Test void refusesPayloadWriteWhenManagedTemplateIsDisabled() {
        jdbc.update("update sys_import_export_template set status='DISABLED' where id=?", TEMPLATE);
        assertThatThrownBy(() -> store.freeze(JOB, prepared(STUDENT), Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job_payload where job_id=?", Integer.class, JOB)).isZero();
    }

    @Test void refusesDuplicateAndLeavesOriginalContentUntouched() {
        store.freeze(JOB, prepared(STUDENT), Template.SIMPLE);
        assertThatThrownBy(() -> store.freeze(JOB, prepared(STUDENT), Template.DETAILED)).isInstanceOf(RuntimeException.class);
        assertThat(store.load(jobs.findById(JOB)).template()).isEqualTo(Template.SIMPLE);
    }

    @Test void refusesWrongOwnerAndStudentBeforeWriting() {
        assertThatThrownBy(() -> store.freeze(JOB, new Prepared(USER + 99, STUDENT, prepared(STUDENT).reports()), Template.SIMPLE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.freeze(JOB, prepared(STUDENT + 99), Template.SIMPLE)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job_payload where job_id=?", Integer.class, JOB)).isZero();
    }

    @Test void refusesLateWriteAfterClaim() {
        jobs.claim(JOB, 0L);
        assertThatThrownBy(() -> store.freeze(JOB, prepared(STUDENT), Template.SIMPLE)).isInstanceOf(IllegalStateException.class);
    }

    @Test void databaseRejectsPdfJobWithoutStudentOrWithSensitiveFlag() {
        assertThatThrownBy(() -> jdbc.update("update sys_export_job set student_id=null where id=?", JOB))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update sys_export_job set sensitive_flag=1 where id=?", JOB))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void detectsCorruptStoredContent() {
        store.freeze(JOB, prepared(STUDENT), Template.SIMPLE);
        jdbc.update("update sys_export_job_payload set payload_json='{}' where job_id=?", JOB);
        assertThatThrownBy(() -> store.load(jobs.findById(JOB))).isInstanceOf(IllegalStateException.class).hasMessageContaining("完整性");
    }

    @Test void rejectsMissingEmptyAndMixedStudentPayloads() {
        assertThatThrownBy(() -> store.load(jobs.findById(JOB))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.freeze(JOB, new Prepared(USER, STUDENT, List.of()), Template.SIMPLE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.freeze(JOB, new Prepared(USER, STUDENT, prepared(STUDENT + 1).reports()), Template.SIMPLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rollsBackPayloadWithJobAndRequiresCallerTransaction() {
        store.freeze(JOB, prepared(STUDENT), Template.SIMPLE);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job where id=?", Integer.class, JOB)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_export_job_payload where job_id=?", Integer.class, JOB)).isZero();
        assertThatThrownBy(() -> store.freeze(JOB, prepared(STUDENT), Template.SIMPLE))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private Prepared prepared(long student) {
        var day = LocalDate.of(2026, 9, 1);
        var report = new GrowthReviewDetailView(1874244142494650110L, student, "测试学生", GrowthReviewPeriodType.DAY,
                day, day, 1874244142494650111L, 1, 1, 1, 0, 0, 0, BigDecimal.ONE, 10, 0,
                day.atStartOfDay(), day.atStartOfDay(), List.of(), List.of(), List.of());
        return new Prepared(USER, student, List.of(report));
    }
}
