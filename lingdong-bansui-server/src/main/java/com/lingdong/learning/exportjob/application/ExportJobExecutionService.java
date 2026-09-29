package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.FileRelation;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.exportjob.application.adapter.ExportAdapterRegistry;
import com.lingdong.learning.exportjob.application.adapter.ExportDataPage;
import com.lingdong.learning.exportjob.application.adapter.ExportDatasetAdapter;
import com.lingdong.learning.exportjob.application.adapter.ExportRequestDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateDefinition;
import com.lingdong.learning.exportjob.application.template.ExportTemplateParser;
import com.lingdong.learning.exportjob.application.template.ExportWorkbookWriter;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.config.ExportJobProperties;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** 流式生成导出文件，并对附件和终态失败执行完整补偿。 */
@Service
public class ExportJobExecutionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportJobExecutionService.class);
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportJobAccessService accessService;
    private final ExportAdapterRegistry adapterRegistry;
    private final ImportExportTemplateMapper templateMapper;
    private final ManagedAttachmentContentService contentService;
    private final ExportTemplateParser templateParser;
    private final ExportWorkbookWriter workbookWriter;
    private final AttachmentFileApplicationService fileService;
    private final ManagedFileMapper managedFileMapper;
    private final ExportJobMapper jobMapper;
    private final ExportJobCompletionService completionService;
    private final ExportJobFailureService failureService;
    private final ObjectMapper objectMapper;
    private final ExportJobProperties properties;
    private final GrowthReviewPdfArtifactService pdfArtifacts;

    public ExportJobExecutionService(
            ExportJobAccessService accessService,
            ExportAdapterRegistry adapterRegistry,
            ImportExportTemplateMapper templateMapper,
            ManagedAttachmentContentService contentService,
            ExportTemplateParser templateParser,
            ExportWorkbookWriter workbookWriter,
            AttachmentFileApplicationService fileService,
            ManagedFileMapper managedFileMapper,
            ExportJobMapper jobMapper,
            ExportJobCompletionService completionService,
            ExportJobFailureService failureService,
            ObjectMapper objectMapper,
            ExportJobProperties properties,
            GrowthReviewPdfArtifactService pdfArtifacts
    ) {
        this.accessService = accessService;
        this.adapterRegistry = adapterRegistry;
        this.templateMapper = templateMapper;
        this.contentService = contentService;
        this.templateParser = templateParser;
        this.workbookWriter = workbookWriter;
        this.fileService = fileService;
        this.managedFileMapper = managedFileMapper;
        this.jobMapper = jobMapper;
        this.completionService = completionService;
        this.failureService = failureService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.pdfArtifacts = pdfArtifacts;
    }

    public boolean execute(ExportJobRecord job) {
        requireExporting(job);
        try {
            if (job.exportType() == ExportJobType.GROWTH_REVIEW_PDF) {
                pdfArtifacts.requireExecution(job);
            } else {
                accessService.requireExecution(job);
            }
        } catch (RuntimeException accessFailure) {
            fail(job.id(), job.versionNo(), "EXPORT_ACCESS_REVOKED",
                    "当前导出权限或范围已失效", accessFailure);
            return false;
        }

        Path temporaryFile = null;
        ManagedFile resultFile = null;
        FileRelation relation = null;
        PageIterator rows = null;
        long expectedVersion = job.versionNo();
        try {
            if (job.exportType() == ExportJobType.GROWTH_REVIEW_PDF) {
                var artifact = pdfArtifacts.generate(job);
                resultFile = contentService.store(job.requesterId(), "EXPORT_JOB", "REPORT_EXPORT",
                        artifact.fileName(), artifact.contentType(), artifact.content());
                relation = fileService.attachToBusiness(new AttachFileToBusinessCommand(
                        resultFile.id(), "EXPORT_JOB", job.id(), "EXPORT_JOB_RESULT", "BUSINESS_AUTHORIZED"));
                // 文件保存期间可能发生撤权；提交终态前再次检查，失败沿用下方统一补偿。
                pdfArtifacts.requireExecution(job);
                completionService.complete(job, expectedVersion, resultFile.id(), artifact.reportCount(), artifact.reportCount());
                return true;
            }
            ExportDatasetAdapter adapter = adapterRegistry.require(job.exportType());
            ExportFilterSnapshot filter = read(job.filterSnapshot(), ExportFilterSnapshot.class,
                    "导出筛选快照无法解析");
            ExportScopeSnapshot scope = read(job.scopeSnapshot(), ExportScopeSnapshot.class,
                    "导出范围快照无法解析");
            List<ExportColumnSnapshot> columns = read(
                    job.columnSnapshot(), new TypeReference<>() { }, "导出字段快照无法解析");
            requireScopeMatches(job, scope);
            ImportExportTemplateRecord template = requireFrozenTemplate(job);
            AttachmentContentView templateContent = contentService.read(template.fileId());
            ExportTemplateDefinition definition = templateParser.parse(
                    templateContent.content(), adapter.columns(),
                    columns.stream().map(ExportColumnSnapshot::code).toList());
            ExportRequestDefinition request = new ExportRequestDefinition(
                    job.requesterId(), scope.studentId(), filter.startedAt(),
                    filter.endedAt(), filter.eventType(), filter.dictionaryTypeCode(), filter.dictionaryStatus(),
                    filter.templateType(), filter.templateModuleCode(), filter.templateStatus(),
                    filter.interfaceCallerName(), filter.interfaceStatus(),
                    filter.interfaceOwnerId() == null ? null : Long.valueOf(filter.interfaceOwnerId()), filter.cacheDomain(), filter.cacheStatus(),
                    filter.systemTaskType(), filter.systemTaskStatus(), scope.systemTaskAuditor(), scope.systemTaskTypes(), filter.rewardExchangeStatus(), filter.exceptionType(), filter.exceptionStatus(),
                    scope.exceptionTeacherOnly(), scope.exceptionClassIds(), filter.attachmentModuleCode(), filter.attachmentUploaderId(), filter.attachmentFileCategory(), filter.studentTaskSource(), filter.studentTaskStatus(), scope.studentTaskAssignmentIds(),
                    scope.orgStatOrgIds(), scope.attClassIds());
            long totalRows = adapter.count(request, scope.upperBound());
            rows = new PageIterator(
                    adapter, request, scope.upperBound(), totalRows,
                    validatedPageSize(), job, expectedVersion);
            boolean tablePdf = (job.exportType() == ExportJobType.STUDENT_TASK_REPORT
                    || job.exportType() == ExportJobType.ORGANIZATION_TASK_STATISTICS
                    || job.exportType() == ExportJobType.ATTENDANCE_LEDGER) && "PDF".equals(filter.outputFormat());
            if (filter.outputFormat() != null && !List.of("XLSX", "PDF").contains(filter.outputFormat())) throw new IllegalStateException("导出格式快照无效");
            if ("PDF".equals(filter.outputFormat()) && !tablePdf) throw new IllegalStateException("当前数据集不支持 PDF");
            temporaryFile = tablePdf
                    ? new com.lingdong.learning.exportjob.application.template.ExportTablePdfWriter().write(definition, rows, properties.getTempDirectory(),
                            job.exportType() == ExportJobType.ORGANIZATION_TASK_STATISTICS ? "灵动伴随 · 机构任务统计"
                                                        : job.exportType() == ExportJobType.ATTENDANCE_LEDGER ? "灵动伴随 · 考勤台账" : "灵动伴随 · 学生任务报表")
                    : workbookWriter.write(templateContent.content(), definition, rows, validatedSheetRows(), properties.getTempDirectory());
            if (rows.processedRows() != totalRows) {
                throw new IllegalStateException("导出数据总量在生成过程中发生不一致");
            }
            expectedVersion = rows.currentVersion();
            byte[] output = Files.readAllBytes(temporaryFile);
            resultFile = contentService.store(
                    job.requesterId(), "EXPORT_JOB", "REPORT_EXPORT",
                    "导出结果-" + job.jobCode() + (tablePdf ? ".pdf" : ".xlsx"), tablePdf ? "application/pdf" : XLSX_CONTENT_TYPE, output);
            relation = fileService.attachToBusiness(new AttachFileToBusinessCommand(
                    resultFile.id(), "EXPORT_JOB", job.id(),
                    "EXPORT_JOB_RESULT", "BUSINESS_AUTHORIZED"));
            // 文件落盘期间也可能撤权；失败沿用附件关系和内容补偿。
            accessService.requireExecution(job);
            completionService.complete(
                    job, expectedVersion, resultFile.id(), totalRows, rows.processedRows());
            return true;
        } catch (IOException | RuntimeException generationFailure) {
            if (rows != null) {
                expectedVersion = rows.currentVersion();
            }
            compensate(relation, resultFile, generationFailure);
            fail(job.id(), expectedVersion, "EXPORT_GENERATION_FAILED",
                    "导出文件生成失败", generationFailure);
            return false;
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private void requireExporting(ExportJobRecord job) {
        if (job == null || job.status() != ExportJobStatus.EXPORTING || job.versionNo() == null) {
            throw new IllegalStateException("只有生成中的导出作业可以执行");
        }
    }

    private ImportExportTemplateRecord requireFrozenTemplate(ExportJobRecord job) {
        ImportExportTemplateRecord template = templateMapper.findById(job.templateId());
        if (template == null || template.templateType() != TemplateType.EXPORT
                || !job.exportType().templateModule().equals(template.moduleCode())
                || !job.templateName().equals(template.templateName())
                || !job.templateVersion().equals(template.version())) {
            throw new IllegalStateException("导出作业固化模板不存在或版本不一致");
        }
        return template;
    }

    private void requireScopeMatches(ExportJobRecord job, ExportScopeSnapshot scope) {
        if (scope.upperBound() < 0 || !java.util.Objects.equals(job.studentId(), scope.studentId())) {
            throw new IllegalStateException("导出范围快照与作业不一致");
        }
    }

    private int validatedPageSize() {
        int size = properties.getQueryPageSize();
        if (size < 1 || size > 10_000) {
            throw new IllegalStateException("导出查询分页大小必须为1至10000");
        }
        return size;
    }

    private int validatedSheetRows() {
        int rows = properties.getSheetMaxRows();
        if (rows < 1 || rows > 1_048_575) {
            throw new IllegalStateException("导出单工作表行数必须为1至1048575");
        }
        return rows;
    }

    private <T> T read(String value, Class<T> type, String message) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private <T> T read(String value, TypeReference<T> type, String message) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private void compensate(
            FileRelation relation,
            ManagedFile resultFile,
            Exception originalFailure
    ) {
        if (relation != null) {
            try {
                fileService.releaseBusinessRelation(relation.id());
            } catch (RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
        if (resultFile != null) {
            try {
                if (managedFileMapper.markRetired(resultFile.id()) != 1) {
                    throw new IllegalStateException("导出结果附件退役失败");
                }
            } catch (RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
            try {
                contentService.discardContent(resultFile.storageKey());
            } catch (RuntimeException cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private void fail(
            Long jobId,
            long expectedVersion,
            String code,
            String message,
            Exception originalFailure
    ) {
        try {
            failureService.fail(jobId, expectedVersion, code, message);
        } catch (RuntimeException statusFailure) {
            originalFailure.addSuppressed(statusFailure);
            if (originalFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("导出失败状态保存失败", originalFailure);
        }
        LOGGER.warn("导出作业执行失败，作业标识={}，异常类型={}",
                jobId, originalFailure.getClass().getSimpleName());
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            LOGGER.warn("导出临时文件清理失败，异常类型={}", exception.getClass().getSimpleName());
        }
    }

    /** 仅在写入器消费数据时拉取下一页，并同步推进数据库进度版本。 */
    private final class PageIterator implements Iterator<Map<String, Object>> {
        private final ExportDatasetAdapter adapter;
        private final ExportRequestDefinition request;
        private final long upperBound;
        private final long totalRows;
        private final int pageSize;
        private final Long jobId;
        private final ExportJobRecord job;
        private Iterator<Map<String, Object>> current = List.<Map<String, Object>>of().iterator();
        private long cursor;
        private long processedRows;
        private long currentVersion;
        private boolean more = true;

        private PageIterator(
                ExportDatasetAdapter adapter,
                ExportRequestDefinition request,
                long upperBound,
                long totalRows,
                int pageSize,
                ExportJobRecord job,
                long initialVersion
        ) {
            this.adapter = adapter;
            this.request = request;
            this.upperBound = upperBound;
            this.totalRows = totalRows;
            this.pageSize = pageSize;
            this.job = job;
            this.jobId = job.id();
            this.currentVersion = initialVersion;
        }

        @Override
        public boolean hasNext() {
            while (!current.hasNext() && more) {
                loadNextPage();
            }
            return current.hasNext();
        }

        @Override
        public Map<String, Object> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }

        private void loadNextPage() {
            accessService.requireExecution(job);
            ExportDataPage page = adapter.fetchAfter(request, upperBound, cursor, pageSize);
            if (page.rows().isEmpty() && page.hasMore()) {
                throw new IllegalStateException("导出适配器返回了无法推进的空分页");
            }
            if (!page.rows().isEmpty()
                    && (page.nextCursor() == null || page.nextCursor() <= cursor
                    || page.nextCursor() > upperBound)) {
                throw new IllegalStateException("导出适配器游标未按上界单调推进");
            }
            processedRows += page.rows().size();
            if (processedRows > totalRows) {
                throw new IllegalStateException("导出分页数据超过创建时统计总量");
            }
            if (jobMapper.updateProgress(
                    jobId, currentVersion, totalRows, processedRows) != 1) {
                throw new IllegalStateException("导出作业进度更新冲突：" + jobId);
            }
            currentVersion++;
            current = page.rows().iterator();
            if (page.nextCursor() != null) {
                cursor = page.nextCursor();
            }
            more = page.hasMore();
        }

        private long processedRows() {
            return processedRows;
        }

        private long currentVersion() {
            return currentVersion;
        }
    }
}
