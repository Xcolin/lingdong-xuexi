package com.lingdong.learning.studentimport.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentImportBatchServiceTest {
    @Test
    void conditionallyClaimsProcessesEveryRowAndContinuesAfterOneFailure() {
        StudentImportExecutionMapper executionMapper = mock(StudentImportExecutionMapper.class);
        StudentImportRowMapper rowMapper = mock(StudentImportRowMapper.class);
        ImportJobMapper importJobMapper = mock(ImportJobMapper.class);
        ManagedAttachmentContentService contentService = mock(ManagedAttachmentContentService.class);
        StudentImportWorkbookReader reader = mock(StudentImportWorkbookReader.class);
        StudentImportAccessService accessService = mock(StudentImportAccessService.class);
        StudentImportRowProcessor rowProcessor = mock(StudentImportRowProcessor.class);
        StudentImportRowFailureService failureService = mock(StudentImportRowFailureService.class);
        StudentImportCredentialService credentialService = mock(StudentImportCredentialService.class);
        StudentImportBatchService service = new StudentImportBatchService(
                executionMapper, rowMapper, importJobMapper, contentService, reader,
                accessService, rowProcessor, failureService, credentialService,
                new ObjectMapper(), 10, 3);
        StudentImportExecutionRecord queued = execution(StudentImportExecutionStatus.QUEUED, 0L);
        StudentImportExecutionRecord running = execution(StudentImportExecutionStatus.RUNNING, 1L);
        StudentImportRowRecord first = row(11L, 2);
        StudentImportRowRecord second = row(12L, 3);
        StudentImportRowRecord exhausted = failedRow(13L, 4, 3);
        when(executionMapper.findQueued(10)).thenReturn(List.of(queued));
        when(executionMapper.claim(queued.id(), 0L)).thenReturn(1);
        when(executionMapper.findById(queued.id())).thenReturn(running);
        when(importJobMapper.findById(queued.validationJobId())).thenReturn(validationJob());
        when(contentService.read(31L)).thenReturn(new AttachmentContentView(
                "students.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[] {1}));
        when(reader.read(any(), any())).thenReturn(List.of(
                new StudentImportWorkbookRow(2, "张同学", null),
                new StudentImportWorkbookRow(3, "李同学", "G4")));
        when(rowMapper.findByExecutionIdAndStatus(
                running.id(), StudentImportRowStatus.PENDING, 0, 2))
                .thenReturn(List.of(first, second));
        when(rowMapper.findByExecutionIdAndStatus(
                running.id(), StudentImportRowStatus.FAILED, 0, 2))
                .thenReturn(List.of(exhausted));
        doThrow(new IllegalStateException("班级停用"))
                .when(rowProcessor).process(running, second, new StudentImportWorkbookRow(3, "李同学", "G4"));

        assertThat(service.processAvailable()).isEqualTo(1);

        verify(rowProcessor).process(running, first, new StudentImportWorkbookRow(2, "张同学", null));
        verify(failureService).markFailed(second.id(), "STUDENT_IMPORT_ROW_FAILED",
                "该行学员开户或班级绑定失败");
        verify(rowProcessor, never()).process(running, exhausted, null);
        verify(credentialService).finalizeExecution(running);
    }

    private StudentImportExecutionRecord execution(StudentImportExecutionStatus status, long version) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 18, 0);
        return new StudentImportExecutionRecord(
                1L, "SIM-1", 2L, 3L, 4L, 5L, status, version,
                2, 0, 0, 0, null, null, null, StudentImportCredentialStatus.NONE,
                null, null, now, status == StudentImportExecutionStatus.RUNNING ? now : null,
                null, now, now);
    }

    private StudentImportRowRecord row(long id, int rowNumber) {
        return new StudentImportRowRecord(id, 1L, rowNumber, StudentImportRowStatus.PENDING,
                null, null, null, null, null, null, null, 0, null, null);
    }

    private StudentImportRowRecord failedRow(long id, int rowNumber, int attempts) {
        return new StudentImportRowRecord(id, 1L, rowNumber, StudentImportRowStatus.FAILED,
                null, null, "STUDENT_IMPORT_ROW_FAILED", "导入失败",
                null, null, null, attempts, null, null);
    }

    private ImportJobRecord validationJob() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 17, 0);
        return new ImportJobRecord(
                2L, "IMP-2", 21L, "V1", "学员导入",
                "[{\"fieldCode\":\"STUDENT_NAME\",\"columnName\":\"学员姓名\",\"dataType\":\"TEXT\",\"required\":true,\"maxLength\":64,\"dictionaryValues\":[],\"sortOrder\":10}]",
                31L, null, 3L, 4L, ImportJobStatus.VALIDATED, 1L,
                null, null, 2, 2, 2, 0, now, now, now, now, now);
    }
}
