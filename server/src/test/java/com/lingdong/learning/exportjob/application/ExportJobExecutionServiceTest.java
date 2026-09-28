package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.FileRelation;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.domain.FileRelationStatus;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry;
import com.lingdong.learning.exportjob.application.adapter.ExportDataPage;
import com.lingdong.learning.exportjob.application.adapter.ExportDatasetAdapter;
import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateParser;
import com.lingdong.learning.exportjob.application.template.ExportWorkbookWriter;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobExecutionServiceTest {
    private static final long JOB_ID = 1874244142494646980L;
    private static final long REQUESTER_ID = 1874244142494646981L;
    private static final long STUDENT_ID = 1874244142494646982L;
    private static final long TEMPLATE_ID = 1874244142494646983L;
    private static final long TEMPLATE_FILE_ID = 1874244142494646984L;
    private static final long RESULT_FILE_ID = 1874244142494646985L;

    @TempDir Path tempDirectory;
    private ExportJobAccessService accessService;
    private ExportAdapterRegistry registry;
    private ExportDatasetAdapter adapter;
    private ImportExportTemplateMapper templateMapper;
    private ManagedAttachmentContentService contentService;
    private ExportTemplateParser parser;
    private ExportWorkbookWriter writer;
    private AttachmentFileApplicationService fileService;
    private ManagedFileMapper managedFileMapper;
    private ExportJobMapper jobMapper;
    private ExportJobCompletionService completionService;
    private ExportJobFailureService failureService;
    private ObjectMapper objectMapper;
    private ExportJobExecutionService service;
    private GrowthReviewPdfArtifactService pdfArtifacts;

    @BeforeEach
    void setUp() throws Exception {
        accessService = mock(ExportJobAccessService.class);
        registry = mock(ExportAdapterRegistry.class);
        adapter = mock(ExportDatasetAdapter.class);
        templateMapper = mock(ImportExportTemplateMapper.class);
        contentService = mock(ManagedAttachmentContentService.class);
        parser = mock(ExportTemplateParser.class);
        writer = mock(ExportWorkbookWriter.class);
        fileService = mock(AttachmentFileApplicationService.class);
        managedFileMapper = mock(ManagedFileMapper.class);
        jobMapper = mock(ExportJobMapper.class);
        completionService = mock(ExportJobCompletionService.class);
        failureService = mock(ExportJobFailureService.class);
        pdfArtifacts = mock(GrowthReviewPdfArtifactService.class);
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        ExportJobProperties properties = new ExportJobProperties();
        properties.setQueryPageSize(2);
        properties.setSheetMaxRows(10);
        properties.setTempDirectory(tempDirectory);
        service = new ExportJobExecutionService(
                accessService, registry, templateMapper, contentService, parser, writer,
                fileService, managedFileMapper, jobMapper, completionService, failureService,
                objectMapper, properties, pdfArtifacts);

        when(registry.require(ExportJobType.GROWTH_POINT_LEDGER)).thenReturn(adapter);
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template());
        when(contentService.read(TEMPLATE_FILE_ID)).thenReturn(new AttachmentContentView(
                "report.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}));
        when(parser.parse(any(), any(), any())).thenReturn(new ExportTemplateDefinition(
                List.of(new ExportColumnDefinition("OCCURRED_AT", "发生时间", true)),
                Map.of("OCCURRED_AT", 0)));
        when(adapter.columns()).thenReturn(List.of(
                new ExportColumnDefinition("OCCURRED_AT", "发生时间", true)));
        when(adapter.count(any(), eq(1874244142494646999L))).thenReturn(1L);
        when(adapter.fetchAfter(any(), eq(1874244142494646999L), eq(0L), eq(2)))
                .thenReturn(new ExportDataPage(
                        List.of(Map.of("OCCURRED_AT", "2026-09-03 10:00:00")),
                        1874244142494646990L, false));
        when(jobMapper.updateProgress(JOB_ID, 1L, 1L, 1L)).thenReturn(1);
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"),
                eq("导出结果-EXP-" + JOB_ID + ".xlsx"), any(), any()))
                .thenReturn(resultFile());
        when(fileService.attachToBusiness(any())).thenReturn(new FileRelation(
                1874244142494646986L, RESULT_FILE_ID, "EXPORT_JOB", JOB_ID,
                "EXPORT_JOB_RESULT", "BUSINESS_AUTHORIZED", FileRelationStatus.ACTIVE));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Iterator<Map<String, Object>> rows = invocation.getArgument(2, Iterator.class);
            while (rows.hasNext()) {
                rows.next();
            }
            Path output = Files.createTempFile(tempDirectory, "writer-", ".xlsx");
            Files.write(output, new byte[]{9, 8, 7});
            return output;
        }).when(writer).write(any(), any(), any(), eq(10), eq(tempDirectory));
    }

    @Test
    void streamsPagesStoresUnifiedAttachmentAndCompletesWithLatestVersion() {
        ExportJobRecord job = job(false, null);

        assertThat(service.execute(job)).isTrue();

        verify(accessService, org.mockito.Mockito.times(3)).requireExecution(job);
        verify(adapter).fetchAfter(any(), eq(1874244142494646999L), eq(0L), eq(2));
        verify(jobMapper).updateProgress(JOB_ID, 1L, 1L, 1L);
        verify(contentService).store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"),
                eq("导出结果-EXP-" + JOB_ID + ".xlsx"), any(), eq(new byte[]{9, 8, 7}));
        verify(fileService).attachToBusiness(any());
        verify(completionService).complete(job, 2L, RESULT_FILE_ID, 1L, 1L);
        verify(failureService, never()).fail(anyLong(), anyLong(), any(), any());
    }

    @Test
    void templateRevocationBeforeNextPageStopsReading() throws Exception {
        var job = templateJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("已撤权"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter, never()).fetchAfter(any(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(contentService, never()).store(any(), any(), any(), any(), any(), any());
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
    }

    @Test
    void templateRevocationAfterAttachmentSaveCompensatesInsteadOfSucceeding() throws Exception {
        var job = templateJob();
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    doThrow(new IllegalStateException("已撤权")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
    }

    private ExportJobRecord templateJob() throws Exception {
        var job = mock(ExportJobRecord.class, org.mockito.AdditionalAnswers.delegatesTo(job(false, null)));
        org.mockito.Mockito.doReturn(ExportJobType.TEMPLATE_LEDGER).when(job).exportType();
        org.mockito.Mockito.doReturn(null).when(job).studentId();
        org.mockito.Mockito.doReturn(objectMapper.writeValueAsString(new ExportScopeSnapshot(null, 1874244142494646999L)))
                .when(job).scopeSnapshot();
        var metadata = mock(ImportExportTemplateRecord.class, org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("TEMPLATE_REPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.TEMPLATE_LEDGER)).thenReturn(adapter);
        return job;
    }

    @Test
    void interfaceRevocationBeforeNextPageStopsReading() throws Exception {
        var job = interfaceJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("已撤权"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter, never()).fetchAfter(any(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(contentService, never()).store(any(), any(), any(), any(), any(), any());
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
    }

    @Test
    void interfaceRevocationAfterAttachmentSaveCompensatesInsteadOfSucceeding() throws Exception {
        var job = interfaceJob();
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    doThrow(new IllegalStateException("已撤权")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
    }

    private ExportJobRecord interfaceJob() throws Exception {
        var job = mock(ExportJobRecord.class, org.mockito.AdditionalAnswers.delegatesTo(job(false, null)));
        org.mockito.Mockito.doReturn(ExportJobType.INTERFACE_SERVICE_LEDGER).when(job).exportType();
        org.mockito.Mockito.doReturn(null).when(job).studentId();
        org.mockito.Mockito.doReturn(objectMapper.writeValueAsString(new ExportScopeSnapshot(null, 1874244142494646999L)))
                .when(job).scopeSnapshot();
        var metadata = mock(ImportExportTemplateRecord.class, org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("INTERFACE_REPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.INTERFACE_SERVICE_LEDGER)).thenReturn(adapter);
        return job;
    }

    @Test
    void storesPdfThroughUnifiedAttachmentAndCompletesWithoutXlsxParser() throws Exception {
        var job = pdfJob();
        when(pdfArtifacts.generate(job)).thenReturn(new GrowthReviewPdfArtifactService.Artifact("复盘.pdf", "application/pdf", new byte[]{1, 2}, 1));
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"), eq("复盘.pdf"), eq("application/pdf"), any()))
                .thenReturn(resultFile());
        assertThat(service.execute(job)).isTrue();
        verify(completionService).complete(job, 1L, RESULT_FILE_ID, 1L, 1L);
        verify(fileService).attachToBusiness(any());
        verify(parser, never()).parse(any(), any(), any());
        verify(accessService, never()).requireExecution(job);
        verify(pdfArtifacts, org.mockito.Mockito.times(2)).requireExecution(job);
    }

    @Test
    void compensatesPdfWhenPostSavePermissionIsRevoked() throws Exception {
        var job = pdfJob();
        when(pdfArtifacts.generate(job)).thenReturn(new GrowthReviewPdfArtifactService.Artifact("复盘.zip", "application/zip", new byte[]{1}, 2));
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"), eq("复盘.zip"), any(), any())).thenReturn(resultFile());
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("已撤权")).when(pdfArtifacts).requireExecution(job);
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
        verify(failureService).fail(JOB_ID, 1L, "EXPORT_GENERATION_FAILED", "导出文件生成失败");
    }

    @Test
    void compensatesPdfWhenCompletionFails() throws Exception {
        var job = pdfJob();
        when(pdfArtifacts.generate(job)).thenReturn(new GrowthReviewPdfArtifactService.Artifact("复盘.pdf", "application/pdf", new byte[]{1}, 1));
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"), eq("复盘.pdf"), any(), any())).thenReturn(resultFile());
        doThrow(new IllegalStateException("终态冲突")).when(completionService).complete(job, 1L, RESULT_FILE_ID, 1L, 1L);
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(contentService).discardContent("attachment/export-result");
    }

    @Test
    void refusesPdfBeforeGenerationWhenAccessIsRevoked() throws Exception {
        var job = pdfJob();
        doThrow(new IllegalStateException("已停用")).when(pdfArtifacts).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(pdfArtifacts, never()).generate(any());
        verify(failureService).fail(JOB_ID, 1L, "EXPORT_ACCESS_REVOKED", "当前导出权限或范围已失效");
    }

    private ExportJobRecord pdfJob() {
        var job = mock(ExportJobRecord.class);
        when(job.id()).thenReturn(JOB_ID);
        when(job.requesterId()).thenReturn(REQUESTER_ID);
        when(job.studentId()).thenReturn(STUDENT_ID);
        when(job.status()).thenReturn(ExportJobStatus.EXPORTING);
        when(job.versionNo()).thenReturn(1L);
        when(job.exportType()).thenReturn(ExportJobType.GROWTH_REVIEW_PDF);
        return job;
    }

    @Test
    void revokesBeforeReadAndPersistsOnlyNeutralFailure() {
        ExportJobRecord job = job(false, null);
        doThrow(new IllegalStateException("模拟包含敏感范围的鉴权详情"))
                .when(accessService).requireExecution(job);

        assertThat(service.execute(job)).isFalse();

        verify(templateMapper, never()).findById(anyLong());
        verify(failureService).fail(
                JOB_ID, 1L, "EXPORT_ACCESS_REVOKED", "当前导出权限或范围已失效");
    }

    @Test
    void compensatesRelationMetadataAndContentWhenFinalCommitFails() {
        ExportJobRecord job = job(false, null);
        doThrow(new IllegalStateException("模拟结果文件唯一绑定冲突"))
                .when(completionService).complete(job, 2L, RESULT_FILE_ID, 1L, 1L);
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);

        assertThat(service.execute(job)).isFalse();

        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(failureService).fail(
                JOB_ID, 2L, "EXPORT_GENERATION_FAILED", "导出文件生成失败");
    }

    @Test
    void completionMarksSensitiveTaskEffectiveOnlyAfterJobSuccess() {
        ExportJobMapper mapper = mock(ExportJobMapper.class);
        ExportJobEventService eventService = mock(ExportJobEventService.class);
        SystemTaskApplicationService taskService = mock(SystemTaskApplicationService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
        ExportJobCompletionService completion = new ExportJobCompletionService(
                mapper, eventService, taskService, clock);
        ExportJobRecord sensitive = job(true, 1874244142494646987L);
        when(mapper.succeed(JOB_ID, 1L, RESULT_FILE_ID, 1L, 1L,
                LocalDateTime.of(2026, 9, 3, 14, 0))).thenReturn(1);

        completion.complete(sensitive, 1L, RESULT_FILE_ID, 1L, 1L);

        verify(eventService).record(JOB_ID, ExportJobEventType.SUCCEEDED, null, "导出文件生成成功");
        verify(taskService).markEffective(1874244142494646987L);
    }

    private ExportJobRecord job(boolean sensitive, Long systemTaskId) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 13, 0);
        try {
            return new ExportJobRecord(
                    JOB_ID, "EXP-" + JOB_ID, sensitive ? ExportJobType.IAM_CHANGE_AUDIT
                    : ExportJobType.GROWTH_POINT_LEDGER, TEMPLATE_ID, "通用报表模板", "V1",
                    REQUESTER_ID, sensitive ? null : STUDENT_ID, systemTaskId,
                    objectMapper.writeValueAsString(new ExportFilterSnapshot(null, null, null)),
                    objectMapper.writeValueAsString(List.of(
                            new ExportColumnSnapshot("OCCURRED_AT", "发生时间"))),
                    objectMapper.writeValueAsString(new ExportScopeSnapshot(
                            sensitive ? null : STUDENT_ID, 1874244142494646999L)),
                    objectMapper.writeValueAsString(new ExportMaskPolicySnapshot("FAMILY_NAME_STAR", 1)),
                    "导出原因", sensitive, ExportJobStatus.EXPORTING, 1L, null,
                    0L, 0L, null, null, "0".repeat(64), now.minusHours(1), null,
                    now.minusMinutes(30), now, null, now.minusHours(1), now);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ImportExportTemplateRecord template() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 13, 0);
        return new ImportExportTemplateRecord(
                TEMPLATE_ID, "通用报表模板", TemplateType.EXPORT, "REPORT", "V1",
                TEMPLATE_FILE_ID, true, "DEFAULT", ImportExportTemplateStatus.ENABLED,
                0L, now, now, "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 3L);
    }

    private ManagedFile resultFile() {
        return new ManagedFile(
                RESULT_FILE_ID, "attachment/export-result", "result.xlsx", "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                3L, REQUESTER_ID, "EXPORT_JOB", "REPORT_EXPORT", "0".repeat(64),
                FileStatus.AVAILABLE);
    }
    @Test
    void cacheRevocationBeforeNextPageStopsReading() throws Exception {
        var job = cacheJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("已撤权"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter, never()).fetchAfter(any(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        verify(contentService, never()).store(any(), any(), any(), any(), any(), any());
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
    }

    @Test
    void cacheRevocationAfterAttachmentSaveCompensatesInsteadOfSucceeding() throws Exception {
        var job = cacheJob();
        when(contentService.store(eq(REQUESTER_ID), eq("EXPORT_JOB"), eq("REPORT_EXPORT"), any(), any(), any()))
                .thenAnswer(invocation -> {
                    doThrow(new IllegalStateException("已撤权")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService, never()).complete(any(), anyLong(), any(), anyLong(), anyLong());
    }

    @Test
    void systemTaskRevocationBeforePageStopsReading() throws Exception {
        var job=taskLedgerJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("领域已撤权"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter,never()).fetchAfter(any(),anyLong(),anyLong(),org.mockito.ArgumentMatchers.anyInt());
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    @Test
    void rewardRelationshipRevocationBeforePageStopsReading() throws Exception {
        var job=rewardLedgerJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("主家长关系已撤销"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter,never()).fetchAfter(any(),anyLong(),anyLong(),org.mockito.ArgumentMatchers.anyInt());
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    @Test
    void rewardRevocationAfterSaveCompensatesFamilyFile() throws Exception {
        var job=rewardLedgerJob();
        when(contentService.store(eq(REQUESTER_ID),eq("EXPORT_JOB"),eq("REPORT_EXPORT"),any(),any(),any()))
                .thenAnswer(invocation->{
                    doThrow(new IllegalStateException("主家长关系已撤销")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    @Test
    void exceptionClassRevocationBeforePageStopsReading() throws Exception {
        var job=exceptionLedgerJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("班级授权已撤销"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter,never()).fetchAfter(any(),anyLong(),anyLong(),org.mockito.ArgumentMatchers.anyInt());
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    @Test
    void exceptionRevocationAfterSaveCompensatesFile() throws Exception {
        var job=exceptionLedgerJob();
        when(contentService.store(eq(REQUESTER_ID),eq("EXPORT_JOB"),eq("REPORT_EXPORT"),any(),any(),any()))
                .thenAnswer(invocation->{
                    doThrow(new IllegalStateException("班级授权已撤销")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    @Test
    void attachmentPermissionRevocationBeforePageStopsReading() throws Exception {
        var job=attachmentLedgerJob();
        org.mockito.Mockito.doNothing().doThrow(new IllegalStateException("附件元数据权限已撤销"))
                .when(accessService).requireExecution(job);
        assertThat(service.execute(job)).isFalse();
        verify(adapter,never()).fetchAfter(any(),anyLong(),anyLong(),org.mockito.ArgumentMatchers.anyInt());
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    @Test
    void attachmentRevocationAfterSaveCompensatesFile() throws Exception {
        var job=attachmentLedgerJob();
        when(contentService.store(eq(REQUESTER_ID),eq("EXPORT_JOB"),eq("REPORT_EXPORT"),any(),any(),any()))
                .thenAnswer(invocation->{
                    doThrow(new IllegalStateException("附件元数据权限已撤销")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    private ExportJobRecord attachmentLedgerJob() throws Exception {
        var job = cacheJob();
        org.mockito.Mockito.doReturn(ExportJobType.ATTACHMENT_LEDGER).when(job).exportType();
        var metadata = mock(ImportExportTemplateRecord.class, org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("ATTACHMENT_LEDGER_REPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.ATTACHMENT_LEDGER)).thenReturn(adapter);
        return job;
    }

    private ExportJobRecord exceptionLedgerJob() throws Exception {
        var job = cacheJob();
        org.mockito.Mockito.doReturn(ExportJobType.EXCEPTION_REPORT_LEDGER).when(job).exportType();
        org.mockito.Mockito.doReturn(objectMapper.writeValueAsString(new ExportScopeSnapshot(
                null, 1874244142494646999L, null, null, true, List.of(1874244142494646998L))))
                .when(job).scopeSnapshot();
        var metadata = mock(ImportExportTemplateRecord.class, org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("EXCEPTION_REPORT_EXPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.EXCEPTION_REPORT_LEDGER)).thenReturn(adapter);
        return job;
    }

    private ExportJobRecord rewardLedgerJob() {
        var job=mock(ExportJobRecord.class,org.mockito.AdditionalAnswers.delegatesTo(job(false,null)));
        org.mockito.Mockito.doReturn(ExportJobType.REWARD_EXCHANGE_LEDGER).when(job).exportType();
        var metadata=mock(ImportExportTemplateRecord.class,org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("REWARD_EXCHANGE_REPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.REWARD_EXCHANGE_LEDGER)).thenReturn(adapter);
        return job;
    }

    @Test
    void systemTaskRevocationAfterSaveCompensatesFileAndRejectsSuccess() throws Exception {
        var job=taskLedgerJob();
        when(contentService.store(eq(REQUESTER_ID),eq("EXPORT_JOB"),eq("REPORT_EXPORT"),any(),any(),any()))
                .thenAnswer(invocation->{
                    doThrow(new IllegalStateException("角色已变化")).when(accessService).requireExecution(job);
                    return resultFile();
                });
        when(managedFileMapper.markRetired(RESULT_FILE_ID)).thenReturn(1);
        assertThat(service.execute(job)).isFalse();
        verify(fileService).releaseBusinessRelation(1874244142494646986L);
        verify(managedFileMapper).markRetired(RESULT_FILE_ID);
        verify(contentService).discardContent("attachment/export-result");
        verify(completionService,never()).complete(any(),anyLong(),any(),anyLong(),anyLong());
    }

    private ExportJobRecord taskLedgerJob() throws Exception {
        var job=cacheJob();
        org.mockito.Mockito.doReturn(ExportJobType.SYSTEM_TASK_LEDGER).when(job).exportType();
        org.mockito.Mockito.doReturn(objectMapper.writeValueAsString(new ExportScopeSnapshot(null,1874244142494646999L,
                false,List.of(com.lingdong.learning.audit.application.SystemTaskType.CACHE_CLEAR)))).when(job).scopeSnapshot();
        var metadata=mock(ImportExportTemplateRecord.class,org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("SYSTEM_TASK_REPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.SYSTEM_TASK_LEDGER)).thenReturn(adapter);
        return job;
    }

    private ExportJobRecord cacheJob() throws Exception {
        var job = mock(ExportJobRecord.class, org.mockito.AdditionalAnswers.delegatesTo(job(false, null)));
        org.mockito.Mockito.doReturn(ExportJobType.CACHE_OPERATION_LOG).when(job).exportType();
        org.mockito.Mockito.doReturn(null).when(job).studentId();
        org.mockito.Mockito.doReturn(objectMapper.writeValueAsString(new ExportScopeSnapshot(null, 1874244142494646999L)))
                .when(job).scopeSnapshot();
        var metadata = mock(ImportExportTemplateRecord.class, org.mockito.AdditionalAnswers.delegatesTo(template()));
        org.mockito.Mockito.doReturn("CACHE_REPORT").when(metadata).moduleCode();
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(metadata);
        when(registry.require(ExportJobType.CACHE_OPERATION_LOG)).thenReturn(adapter);
        return job;
    }
}
