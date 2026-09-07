package com.lingdong.learning.importjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.importjob.application.validation.ImportRowValidationResult;
import com.lingdong.learning.importjob.application.validation.ImportValidationErrorWorkbookWriter;
import com.lingdong.learning.importjob.application.validation.WorkbookValidationResult;
import com.lingdong.learning.importjob.application.validation.WorkbookValidationService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobRowResultRecord;
import com.lingdong.learning.importjob.domain.ImportJobRowStatus;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobRowResultMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 保存逐行校验结果、错误文件并以版本条件进入确定终态。 */
@Service
public class ImportJobResultService {
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportJobMapper jobMapper;
    private final ImportJobRowResultMapper rowMapper;
    private final ManagedAttachmentContentService contentService;
    private final AttachmentFileApplicationService fileService;
    private final WorkbookValidationService validationService;
    private final ImportValidationErrorWorkbookWriter errorWriter;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final int maxRows;

    public ImportJobResultService(
            ImportJobMapper jobMapper,
            ImportJobRowResultMapper rowMapper,
            ManagedAttachmentContentService contentService,
            AttachmentFileApplicationService fileService,
            WorkbookValidationService validationService,
            ImportValidationErrorWorkbookWriter errorWriter,
            IdGenerator idGenerator,
            ObjectMapper objectMapper,
            @Value("${lingdong.import-validation.max-rows:10000}") int maxRows
    ) {
        this.jobMapper = jobMapper;
        this.rowMapper = rowMapper;
        this.contentService = contentService;
        this.fileService = fileService;
        this.validationService = validationService;
        this.errorWriter = errorWriter;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.maxRows = maxRows;
    }

    @Transactional
    public void process(ImportJobRecord job) {
        requireValidating(job);
        AttachmentContentView source = contentService.read(job.sourceFileId());
        WorkbookValidationResult result = validationService.validate(
                source.content(), deserializeSnapshot(job.fieldMappingSnapshot()), maxRows);
        List<ImportJobRowResultRecord> rows = toRows(job.id(), result.rows());
        if (!rows.isEmpty() && rowMapper.insertBatch(rows) != rows.size()) {
            throw new IllegalStateException("导入校验逐行结果保存失败");
        }

        ManagedFile errorFile = null;
        try {
            if (!result.errors().isEmpty()) {
                byte[] errorContent = errorWriter.write(result.errors());
                errorFile = contentService.store(
                        job.requesterId(), "IMPORT_JOB", "IMPORT_VALIDATION",
                        "import-errors-" + job.jobCode() + ".xlsx", XLSX_CONTENT_TYPE, errorContent
                );
                fileService.attachToBusiness(new AttachFileToBusinessCommand(
                        errorFile.id(), "IMPORT_JOB", job.id(),
                        "IMPORT_JOB_ERROR", "IMPORT_VALIDATION"
                ));
            }
            ImportJobStatus status = result.valid()
                    ? ImportJobStatus.VALIDATED : ImportJobStatus.VALIDATION_FAILED;
            if (jobMapper.finish(
                    job.id(), job.versionNo(), status, errorFile == null ? null : errorFile.id(),
                    result.totalRows(), result.totalRows(), result.validRows(), result.invalidRows(),
                    null, null
            ) != 1) {
                throw new IllegalStateException("导入校验作业终态更新失败：" + job.id());
            }
        } catch (RuntimeException exception) {
            if (errorFile != null) {
                discardPreservingFailure(errorFile.storageKey(), exception);
            }
            throw exception;
        }
    }

    @Transactional
    public void markSystemFailed(ImportJobRecord job, String failureCode, String failureMessage) {
        requireValidating(job);
        String code = limit(failureCode, 64, "IMPORT_VALIDATION_SYSTEM_ERROR");
        String message = limit(failureMessage, 500, "导入校验处理失败");
        if (jobMapper.markSystemFailed(job.id(), job.versionNo(), code, message) != 1) {
            throw new IllegalStateException("导入校验作业系统失败状态更新冲突：" + job.id());
        }
    }

    private List<ImportFieldMappingSnapshot> deserializeSnapshot(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导入字段映射快照无法解析", exception);
        }
    }

    private List<ImportJobRowResultRecord> toRows(
            Long jobId,
            List<ImportRowValidationResult> results
    ) {
        List<ImportJobRowResultRecord> rows = new ArrayList<>(results.size());
        for (ImportRowValidationResult result : results) {
            rows.add(new ImportJobRowResultRecord(
                    idGenerator.nextId(), jobId, result.rowNumber(),
                    result.valid() ? ImportJobRowStatus.VALID : ImportJobRowStatus.INVALID,
                    result.valid() ? null : limit(result.errorSummary(), 1000, "校验失败"), null
            ));
        }
        return List.copyOf(rows);
    }

    private void requireValidating(ImportJobRecord job) {
        if (job == null || job.status() != ImportJobStatus.VALIDATING) {
            throw new IllegalStateException("只有校验中的作业可以写入结果");
        }
    }

    private String limit(String value, int maxLength, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private void discardPreservingFailure(String storageKey, RuntimeException originalFailure) {
        try {
            contentService.discardContent(storageKey);
        } catch (RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
