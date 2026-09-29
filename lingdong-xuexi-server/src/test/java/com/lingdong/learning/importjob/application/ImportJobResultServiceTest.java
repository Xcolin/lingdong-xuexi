package com.lingdong.learning.importjob.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.importjob.application.validation.ImportCellError;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.importjob.application.validation.ImportRowValidationResult;
import com.lingdong.learning.importjob.application.validation.ImportValidationErrorWorkbookWriter;
import com.lingdong.learning.importjob.application.validation.WorkbookValidationResult;
import com.lingdong.learning.importjob.application.validation.WorkbookValidationService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobRowResultMapper;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobResultServiceTest {
    private ImportJobMapper jobMapper;
    private ImportJobRowResultMapper rowMapper;
    private ManagedAttachmentContentService contentService;
    private AttachmentFileApplicationService fileService;
    private WorkbookValidationService validationService;
    private ImportValidationErrorWorkbookWriter errorWriter;
    private IdGenerator idGenerator;
    private ObjectMapper objectMapper;
    private ImportJobResultService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(ImportJobMapper.class);
        rowMapper = mock(ImportJobRowResultMapper.class);
        contentService = mock(ManagedAttachmentContentService.class);
        fileService = mock(AttachmentFileApplicationService.class);
        validationService = mock(WorkbookValidationService.class);
        errorWriter = mock(ImportValidationErrorWorkbookWriter.class);
        idGenerator = mock(IdGenerator.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ImportJobResultService(
                jobMapper, rowMapper, contentService, fileService, validationService,
                errorWriter, idGenerator, objectMapper, 10_000
        );
    }

    @Test
    void finishesAllValidWorkbookAsValidated() throws Exception {
        ImportJobRecord job = job(snapshot());
        when(contentService.read(job.sourceFileId()))
                .thenReturn(new AttachmentContentView("source.xlsx", "application/xlsx", new byte[]{1}));
        when(validationService.validate(any(), any(), eq(10_000))).thenReturn(
                new WorkbookValidationResult(1, 1, 0, List.of(
                        new ImportRowValidationResult(2, true, List.of())
                ), List.of())
        );
        when(idGenerator.nextId()).thenReturn(1874244142494646901L);
        when(rowMapper.insertBatch(any())).thenReturn(1);
        when(jobMapper.finish(
                job.id(), job.versionNo(), ImportJobStatus.VALIDATED, null,
                1, 1, 1, 0, null, null
        )).thenReturn(1);

        service.process(job);

        verify(jobMapper).finish(
                job.id(), job.versionNo(), ImportJobStatus.VALIDATED, null,
                1, 1, 1, 0, null, null
        );
    }

    @Test
    void storesSafeErrorWorkbookAndFinishesInvalidWorkbook() throws Exception {
        ImportJobRecord job = job(snapshot());
        ImportCellError error = new ImportCellError(
                2, "姓名", "NAME", "REQUIRED", "姓名不能为空");
        when(contentService.read(job.sourceFileId()))
                .thenReturn(new AttachmentContentView("source.xlsx", "application/xlsx", new byte[]{1}));
        when(validationService.validate(any(), any(), eq(10_000))).thenReturn(
                new WorkbookValidationResult(1, 0, 1, List.of(
                        new ImportRowValidationResult(2, false, List.of(error))
                ), List.of(error))
        );
        when(errorWriter.write(List.of(error))).thenReturn(new byte[]{9});
        ManagedFile errorFile = new ManagedFile(
                1874244142494646902L, "import/job/error", "error.xlsx", "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1L,
                job.requesterId(), "IMPORT_JOB", "IMPORT_VALIDATION", "0".repeat(64),
                FileStatus.AVAILABLE
        );
        when(contentService.store(any(), any(), any(), any(), any(), any())).thenReturn(errorFile);
        when(idGenerator.nextId()).thenReturn(1874244142494646903L);
        when(rowMapper.insertBatch(any())).thenReturn(1);
        when(jobMapper.finish(
                job.id(), job.versionNo(), ImportJobStatus.VALIDATION_FAILED, errorFile.id(),
                1, 1, 0, 1, null, null
        )).thenReturn(1);

        service.process(job);

        verify(fileService).attachToBusiness(any());
        verify(jobMapper).finish(
                job.id(), job.versionNo(), ImportJobStatus.VALIDATION_FAILED, errorFile.id(),
                1, 1, 0, 1, null, null
        );
    }

    @Test
    void marksValidatingJobAsSystemFailedWithBoundedMessage() throws Exception {
        ImportJobRecord job = job(snapshot());
        when(jobMapper.markSystemFailed(
                job.id(), job.versionNo(), "IMPORT_VALIDATION_SYSTEM_ERROR", "导入校验处理失败"
        )).thenReturn(1);

        service.markSystemFailed(job, null, null);

        verify(jobMapper).markSystemFailed(
                job.id(), job.versionNo(), "IMPORT_VALIDATION_SYSTEM_ERROR", "导入校验处理失败");
    }

    @Test
    void rejectsRepeatedTerminalWrite() throws Exception {
        ImportJobRecord job = job(snapshot());
        when(jobMapper.markSystemFailed(
                job.id(), job.versionNo(), "E", "失败"
        )).thenReturn(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.markSystemFailed(job, "E", "失败"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("状态更新冲突");
    }

    private String snapshot() throws Exception {
        return objectMapper.writeValueAsString(List.of(new ImportFieldMappingSnapshot(
                "NAME", "姓名", ImportTemplateFieldDataType.TEXT, true, 20, Set.of(), 10
        )));
    }

    private ImportJobRecord job(String snapshot) {
        LocalDateTime now = LocalDateTime.now();
        return new ImportJobRecord(
                1874244142494646900L, "IMP-1874244142494646900", 2L, "V1", "模板",
                snapshot, 3L, null, 4L, null, ImportJobStatus.VALIDATING, 1L,
                null, null, 0, 0, 0, 0, now, now, null, now, now
        );
    }
}
