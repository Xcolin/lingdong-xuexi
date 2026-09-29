package com.lingdong.learning.studentimport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 竞争领取排队任务，并以逐行独立事务完成学员导入。 */
@Service
public class StudentImportBatchService {
    private final StudentImportExecutionMapper executionMapper;
    private final StudentImportRowMapper rowMapper;
    private final ImportJobMapper importJobMapper;
    private final ManagedAttachmentContentService contentService;
    private final StudentImportWorkbookReader workbookReader;
    private final StudentImportAccessService accessService;
    private final StudentImportRowProcessor rowProcessor;
    private final StudentImportRowFailureService failureService;
    private final StudentImportCredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final int batchSize;
    private final int maxAttempts;

    public StudentImportBatchService(
            StudentImportExecutionMapper executionMapper,
            StudentImportRowMapper rowMapper,
            ImportJobMapper importJobMapper,
            ManagedAttachmentContentService contentService,
            StudentImportWorkbookReader workbookReader,
            StudentImportAccessService accessService,
            StudentImportRowProcessor rowProcessor,
            StudentImportRowFailureService failureService,
            StudentImportCredentialService credentialService,
            ObjectMapper objectMapper,
            @Value("${lingdong.student-import.batch-size:10}") int batchSize,
            @Value("${lingdong.student-import.max-attempts:3}") int maxAttempts
    ) {
        this.executionMapper = executionMapper;
        this.rowMapper = rowMapper;
        this.importJobMapper = importJobMapper;
        this.contentService = contentService;
        this.workbookReader = workbookReader;
        this.accessService = accessService;
        this.rowProcessor = rowProcessor;
        this.failureService = failureService;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    public int processAvailable() {
        accessService.requireFeatures();
        int processed = 0;
        for (StudentImportExecutionRecord queued : executionMapper.findQueued(batchSize)) {
            if (executionMapper.claim(queued.id(), queued.versionNo()) != 1) {
                continue;
            }
            StudentImportExecutionRecord running = executionMapper.findById(queued.id());
            try {
                processClaimed(running);
            } catch (RuntimeException exception) {
                executionMapper.fail(
                        running.id(), running.versionNo(), "STUDENT_IMPORT_SYSTEM_FAILED",
                        "学员批量导入执行失败", LocalDateTime.now());
            }
            processed++;
        }
        return processed;
    }

    private void processClaimed(StudentImportExecutionRecord execution) {
        if (execution == null || execution.status() != StudentImportExecutionStatus.RUNNING) {
            throw new IllegalStateException("学员导入领取后状态不一致");
        }
        accessService.requireExecute(
                execution.requesterId(), execution.organizationId(), execution.classOrganizationId());
        ImportJobRecord job = importJobMapper.findById(execution.validationJobId());
        if (job == null || job.status() != ImportJobStatus.VALIDATED) {
            throw new IllegalStateException("学员导入关联的校验作业状态无效");
        }
        AttachmentContentView source = contentService.read(job.sourceFileId());
        List<StudentImportWorkbookRow> sourceRows = workbookReader.read(
                source.content(), deserializeMappings(job.fieldMappingSnapshot()));
        Map<Integer, StudentImportWorkbookRow> sourceByNumber = new HashMap<>();
        sourceRows.forEach(row -> sourceByNumber.put(row.rowNumber(), row));

        List<StudentImportRowRecord> rows = new ArrayList<>();
        rows.addAll(rowMapper.findByExecutionIdAndStatus(
                execution.id(), StudentImportRowStatus.PENDING, 0, execution.totalRows()));
        rows.addAll(rowMapper.findByExecutionIdAndStatus(
                execution.id(), StudentImportRowStatus.FAILED, 0, execution.totalRows()));
        for (StudentImportRowRecord row : rows) {
            if (row.attemptCount() != null && row.attemptCount() >= maxAttempts) {
                continue;
            }
            StudentImportWorkbookRow sourceRow = sourceByNumber.get(row.rowNumber());
            if (sourceRow == null) {
                failureService.markFailed(row.id(), "STUDENT_IMPORT_SOURCE_ROW_MISSING",
                        "已校验源文件中找不到对应行");
                continue;
            }
            try {
                rowProcessor.process(execution, row, sourceRow);
            } catch (RuntimeException exception) {
                failureService.markFailed(row.id(), "STUDENT_IMPORT_ROW_FAILED",
                        "该行学员开户或班级绑定失败");
            }
        }
        credentialService.finalizeExecution(execution);
    }

    private List<ImportFieldMappingSnapshot> deserializeMappings(String snapshot) {
        try {
            return List.copyOf(objectMapper.readValue(snapshot, new TypeReference<>() { }));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学员导入字段映射快照无法解析", exception);
        }
    }
}
