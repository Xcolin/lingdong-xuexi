package com.lingdong.learning.studentimport.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;
import com.lingdong.learning.studentimport.domain.StudentImportRowRecord;
import com.lingdong.learning.studentimport.domain.StudentImportRowStatus;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportExecutionMapper;
import com.lingdong.learning.studentimport.infrastructure.persistence.StudentImportRowMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentImportApplicationServiceTest {
    private static final long USER_ID = 1874244142494647031L;
    private static final long SCHOOL_ID = 1874244142494647032L;
    private static final long CLASS_ID = 1874244142494647033L;
    private static final long JOB_ID = 1874244142494647034L;

    private ImportJobMapper importJobMapper;
    private ImportExportTemplateMapper templateMapper;
    private ManagedAttachmentContentService contentService;
    private StudentImportAccessService accessService;
    private StudentImportExecutionMapper executionMapper;
    private StudentImportRowMapper rowMapper;
    private IdGenerator idGenerator;
    private StudentImportWorkbookReader workbookReader;
    private StudentImportApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        importJobMapper = mock(ImportJobMapper.class);
        templateMapper = mock(ImportExportTemplateMapper.class);
        contentService = mock(ManagedAttachmentContentService.class);
        accessService = mock(StudentImportAccessService.class);
        executionMapper = mock(StudentImportExecutionMapper.class);
        rowMapper = mock(StudentImportRowMapper.class);
        idGenerator = mock(IdGenerator.class);
        workbookReader = mock(StudentImportWorkbookReader.class);
        service = new StudentImportApplicationService(
                importJobMapper, templateMapper, contentService, accessService,
                executionMapper, rowMapper, idGenerator, new ObjectMapper(), workbookReader, 3);
        when(importJobMapper.findById(JOB_ID)).thenReturn(job(ImportJobStatus.VALIDATED, USER_ID));
        when(templateMapper.findById(1874244142494647035L)).thenReturn(template("STUDENT"));
        when(contentService.read(1874244142494647036L)).thenReturn(
                new AttachmentContentView("students.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1}));
        when(workbookReader.read(any(), any())).thenReturn(List.of(
                new StudentImportWorkbookRow(2, "张同学", "G3"),
                new StudentImportWorkbookRow(3, "李同学", null)));
        when(idGenerator.nextId()).thenReturn(
                1874244142494647037L, 1874244142494647038L, 1874244142494647039L);
        when(executionMapper.insert(any())).thenReturn(1);
        when(rowMapper.insertBatch(any())).thenReturn(2);
    }

    @Test
    void createsOneQueuedExecutionAndPendingRowsFromValidatedStudentJob() {
        StudentImportExecutionRecord result = service.create(
                new CreateStudentImportCommand(USER_ID, JOB_ID, CLASS_ID));

        assertThat(result.id()).isEqualTo(1874244142494647037L);
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.organizationId()).isEqualTo(SCHOOL_ID);
        assertThat(result.classOrganizationId()).isEqualTo(CLASS_ID);
        verify(accessService).requireExecute(USER_ID, SCHOOL_ID, CLASS_ID);
        verify(executionMapper).insert(any());
        verify(rowMapper).insertBatch(any());
    }

    @Test
    void rejectsWrongStatusOwnerModuleAndDuplicateExecution() {
        when(importJobMapper.findById(JOB_ID)).thenReturn(job(ImportJobStatus.VALIDATING, USER_ID));
        assertThatThrownBy(() -> service.create(new CreateStudentImportCommand(USER_ID, JOB_ID, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("校验通过");

        when(importJobMapper.findById(JOB_ID)).thenReturn(job(ImportJobStatus.VALIDATED, USER_ID + 1));
        assertThatThrownBy(() -> service.create(new CreateStudentImportCommand(USER_ID, JOB_ID, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("本人");

        when(importJobMapper.findById(JOB_ID)).thenReturn(job(ImportJobStatus.VALIDATED, USER_ID));
        when(templateMapper.findById(1874244142494647035L)).thenReturn(template("TEACHER"));
        assertThatThrownBy(() -> service.create(new CreateStudentImportCommand(USER_ID, JOB_ID, null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("STUDENT");

        when(templateMapper.findById(1874244142494647035L)).thenReturn(template("STUDENT"));
        when(executionMapper.findByValidationJobId(JOB_ID)).thenReturn(mock(StudentImportExecutionRecord.class));
        assertThatThrownBy(() -> service.create(new CreateStudentImportCommand(USER_ID, JOB_ID, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已经执行");
    }

    @Test
    void rejectsRetryWhenEveryFailedRowReachedAttemptLimit() {
        StudentImportExecutionRecord execution = execution(StudentImportCredentialStatus.CONSUMED);
        when(executionMapper.findById(execution.id())).thenReturn(execution);
        when(rowMapper.findByExecutionIdAndStatus(
                execution.id(), StudentImportRowStatus.FAILED, 0, execution.totalRows()))
                .thenReturn(List.of(new StudentImportRowRecord(
                        1874244142494647040L, execution.id(), 2, StudentImportRowStatus.FAILED,
                        null, null, "STUDENT_IMPORT_ROW_FAILED", "导入失败",
                        null, null, null, 3, null, null)));

        assertThatThrownBy(() -> service.retryFailures(USER_ID, execution.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("最大重试次数");
    }

    private ImportJobRecord job(ImportJobStatus status, long requesterId) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 17, 0);
        return new ImportJobRecord(
                JOB_ID, "IMP-STUDENT", 1874244142494647035L, "V1", "学员导入",
                "[{\"fieldCode\":\"STUDENT_NAME\",\"columnName\":\"学员姓名\",\"dataType\":\"TEXT\",\"required\":true,\"maxLength\":64,\"dictionaryValues\":[],\"sortOrder\":10}]",
                1874244142494647036L, null, requesterId, SCHOOL_ID, status,
                1L, null, null, 2, 2, 2, 0, now, now, now, now, now);
    }

    private ImportExportTemplateRecord template(String moduleCode) {
        return new ImportExportTemplateRecord(
                1874244142494647035L, "学员导入", TemplateType.IMPORT, moduleCode,
                "V1", 1874244142494647036L, true, "DEFAULT",
                ImportExportTemplateStatus.ENABLED, 0L, null, null,
                "students.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1L);
    }

    private StudentImportExecutionRecord execution(StudentImportCredentialStatus credentialStatus) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 18, 0);
        return new StudentImportExecutionRecord(
                1874244142494647041L, "SIM-1874244142494647041", JOB_ID, USER_ID,
                SCHOOL_ID, null, StudentImportExecutionStatus.PARTIAL_SUCCEEDED, 2L,
                2, 2, 1, 1, null, null, null, credentialStatus,
                null, null, now, now, now, now, now);
    }
}
