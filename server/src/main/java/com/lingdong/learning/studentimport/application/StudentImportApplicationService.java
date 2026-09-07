package com.lingdong.learning.studentimport.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 将已通过 V56 校验的学员文件转换为唯一、可异步执行的业务导入任务。 */
@Service
public class StudentImportApplicationService {
    private final ImportJobMapper importJobMapper;
    private final ImportExportTemplateMapper templateMapper;
    private final ManagedAttachmentContentService contentService;
    private final StudentImportAccessService accessService;
    private final StudentImportExecutionMapper executionMapper;
    private final StudentImportRowMapper rowMapper;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final StudentImportWorkbookReader workbookReader;
    private final int maxAttempts;

    public StudentImportApplicationService(
            ImportJobMapper importJobMapper,
            ImportExportTemplateMapper templateMapper,
            ManagedAttachmentContentService contentService,
            StudentImportAccessService accessService,
            StudentImportExecutionMapper executionMapper,
            StudentImportRowMapper rowMapper,
            IdGenerator idGenerator,
            ObjectMapper objectMapper,
            StudentImportWorkbookReader workbookReader,
            @Value("${lingdong.student-import.max-attempts:3}") int maxAttempts
    ) {
        this.importJobMapper = importJobMapper;
        this.templateMapper = templateMapper;
        this.contentService = contentService;
        this.accessService = accessService;
        this.executionMapper = executionMapper;
        this.rowMapper = rowMapper;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.workbookReader = workbookReader;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public StudentImportExecutionRecord create(CreateStudentImportCommand command) {
        Objects.requireNonNull(command, "学员导入执行创建请求不能为空");
        if (command.operatorId() == null || command.validationJobId() == null) {
            throw new IllegalArgumentException("操作人和校验作业标识不能为空");
        }
        ImportJobRecord job = importJobMapper.findById(command.validationJobId());
        if (job == null || job.organizationId() == null) {
            throw new ResourceNotFoundException("机构学员导入校验作业不存在");
        }
        accessService.requireExecute(
                command.operatorId(), job.organizationId(), command.classOrganizationId());
        if (!Objects.equals(job.requesterId(), command.operatorId())) {
            throw new IllegalArgumentException("只能执行本人创建的导入校验作业");
        }
        if (job.status() != ImportJobStatus.VALIDATED) {
            throw new IllegalStateException("只有校验通过的作业可以执行学员导入");
        }
        if (executionMapper.findByValidationJobId(job.id()) != null) {
            throw new IllegalStateException("该导入校验作业已经执行，不能重复开户");
        }
        ImportExportTemplateRecord template = templateMapper.findById(job.templateId());
        if (template == null || template.templateType() != TemplateType.IMPORT
                || !"STUDENT".equals(template.moduleCode())) {
            throw new IllegalArgumentException("只能执行 STUDENT 类型的导入模板");
        }

        List<ImportFieldMappingSnapshot> mappings = deserializeMappings(job.fieldMappingSnapshot());
        AttachmentContentView source = contentService.read(job.sourceFileId());
        List<StudentImportWorkbookRow> sourceRows = workbookReader.read(source.content(), mappings);
        if (sourceRows.isEmpty() || sourceRows.size() != job.validRows()) {
            throw new IllegalStateException("学员导入源行与已校验结果不一致");
        }

        long executionId = idGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        StudentImportExecutionRecord execution = new StudentImportExecutionRecord(
                executionId, "SIM-" + executionId, job.id(), command.operatorId(),
                job.organizationId(), command.classOrganizationId(),
                StudentImportExecutionStatus.QUEUED, 0L, sourceRows.size(), 0, 0, 0,
                null, null, null, StudentImportCredentialStatus.NONE, null, null,
                now, null, null, now, now);
        if (executionMapper.insert(execution) != 1) {
            throw new IllegalStateException("学员导入执行保存失败");
        }
        List<StudentImportRowRecord> rows = pendingRows(executionId, sourceRows);
        if (rowMapper.insertBatch(rows) != rows.size()) {
            throw new IllegalStateException("学员导入逐行任务保存失败");
        }
        return execution;
    }

    @Transactional
    public StudentImportExecutionRecord retryFailures(Long operatorId, Long executionId) {
        if (operatorId == null || executionId == null) {
            throw new IllegalArgumentException("操作人和学员导入执行标识不能为空");
        }
        StudentImportExecutionRecord execution = executionMapper.findById(executionId);
        if (execution == null) {
            throw new ResourceNotFoundException("学员导入执行不存在");
        }
        accessService.requireExecute(
                operatorId, execution.organizationId(), execution.classOrganizationId());
        if (!Objects.equals(execution.requesterId(), operatorId)) {
            throw new IllegalArgumentException("只能重试本人创建的学员导入执行");
        }
        if (execution.credentialStatus() == StudentImportCredentialStatus.AVAILABLE) {
            throw new IllegalStateException("请先下载或等待已有初始凭证过期后再重试失败行");
        }
        boolean hasRetryableRow = rowMapper.findByExecutionIdAndStatus(
                        execution.id(), StudentImportRowStatus.FAILED, 0, execution.totalRows())
                .stream()
                .anyMatch(row -> row.attemptCount() == null || row.attemptCount() < maxAttempts);
        if (!hasRetryableRow) {
            throw new IllegalStateException("失败行已达到最大重试次数");
        }
        if (executionMapper.requeueFailures(
                execution.id(), execution.versionNo(), LocalDateTime.now()) != 1) {
            throw new IllegalStateException("当前学员导入没有可重试失败行或状态已变化");
        }
        return executionMapper.findById(execution.id());
    }

    private List<StudentImportRowRecord> pendingRows(
            long executionId,
            List<StudentImportWorkbookRow> sourceRows
    ) {
        List<StudentImportRowRecord> rows = new ArrayList<>(sourceRows.size());
        for (StudentImportWorkbookRow sourceRow : sourceRows) {
            rows.add(new StudentImportRowRecord(
                    idGenerator.nextId(), executionId, sourceRow.rowNumber(),
                    StudentImportRowStatus.PENDING, null, null, null, null,
                    null, null, null, 0, null, null));
        }
        return List.copyOf(rows);
    }

    private List<ImportFieldMappingSnapshot> deserializeMappings(String snapshot) {
        try {
            return List.copyOf(objectMapper.readValue(snapshot, new TypeReference<>() { }));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("学员导入字段映射快照无法解析", exception);
        }
    }
}
