package com.lingdong.learning.importjob.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.application.ManagedFile;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateFieldRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateRecord;
import com.lingdong.learning.templateconfig.domain.ImportExportTemplateStatus;
import com.lingdong.learning.templateconfig.domain.ImportTemplateFieldDataType;
import com.lingdong.learning.templateconfig.domain.TemplateType;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateFieldMapper;
import com.lingdong.learning.templateconfig.infrastructure.persistence.ImportExportTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobApplicationServiceTest {
    private static final long OPERATOR_ID = 1874244142494646801L;
    private static final long TEMPLATE_ID = 1874244142494646802L;
    private static final long JOB_ID = 1874244142494646803L;
    private static final long FILE_ID = 1874244142494646804L;

    private ImportJobMapper jobMapper;
    private ImportExportTemplateMapper templateMapper;
    private ImportExportTemplateFieldMapper fieldMapper;
    private DictionaryItemMapper dictionaryItemMapper;
    private ImportJobAccessService accessService;
    private IdGenerator idGenerator;
    private ManagedAttachmentContentService contentService;
    private AttachmentFileApplicationService fileService;
    private ImportJobApplicationService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(ImportJobMapper.class);
        templateMapper = mock(ImportExportTemplateMapper.class);
        fieldMapper = mock(ImportExportTemplateFieldMapper.class);
        dictionaryItemMapper = mock(DictionaryItemMapper.class);
        accessService = mock(ImportJobAccessService.class);
        idGenerator = mock(IdGenerator.class);
        contentService = mock(ManagedAttachmentContentService.class);
        fileService = mock(AttachmentFileApplicationService.class);
        service = new ImportJobApplicationService(
                jobMapper, templateMapper, fieldMapper, dictionaryItemMapper, accessService,
                idGenerator, new ObjectMapper().findAndRegisterModules(), contentService, fileService
        );
    }

    @Test
    void createsQueuedJobWithTemplateAndDictionarySnapshotAndControlledFileRelation() {
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template());
        when(fieldMapper.findByTemplateId(TEMPLATE_ID)).thenReturn(fields());
        when(dictionaryItemMapper.findEnabledByTypeCode("STUDENT_STATUS")).thenReturn(List.of(
                DictionaryItem.enabled(1L, 2L, "ACTIVE", "启用", 10, true),
                DictionaryItem.enabled(2L, 2L, "INACTIVE", "停用", 20, false)
        ));
        when(idGenerator.nextId()).thenReturn(JOB_ID);
        when(contentService.store(
                OPERATOR_ID, "IMPORT_JOB", "IMPORT_VALIDATION", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        )).thenReturn(storedFile());
        when(jobMapper.insert(any())).thenReturn(1);

        ImportJobView result = service.create(new CreateImportJobCommand(
                OPERATOR_ID, TEMPLATE_ID, null, "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3}
        ));

        assertThat(result.id()).isEqualTo(JOB_ID);
        assertThat(Long.toString(result.id())).hasSize(19);
        assertThat(result.status()).isEqualTo(ImportJobStatus.QUEUED);
        assertThat(result.fieldMappingSnapshot()).contains("STUDENT_STATUS", "ACTIVE", "INACTIVE");
        verify(accessService).requireCreate(OPERATOR_ID, null);
        verify(fileService).attachToBusiness(any());
    }

    @Test
    void rejectsNonXlsxBeforeWritingAttachment() {
        assertThatThrownBy(() -> service.create(new CreateImportJobCommand(
                OPERATOR_ID, TEMPLATE_ID, null, "students.csv", "text/csv", new byte[]{1}
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".xlsx");

        verify(contentService, never()).store(any(), any(), any(), any(), any(), any());
    }

    @Test
    void preservesDatabaseFailureAndDiscardsStoredContent() {
        when(templateMapper.findById(TEMPLATE_ID)).thenReturn(template());
        when(fieldMapper.findByTemplateId(TEMPLATE_ID)).thenReturn(fields());
        when(dictionaryItemMapper.findEnabledByTypeCode("STUDENT_STATUS")).thenReturn(List.of(
                DictionaryItem.enabled(1L, 2L, "ACTIVE", "启用", 10, true)
        ));
        when(idGenerator.nextId()).thenReturn(JOB_ID);
        when(contentService.store(any(), any(), any(), any(), any(), any()))
                .thenReturn(storedFile());
        when(jobMapper.insert(any())).thenReturn(0);

        assertThatThrownBy(() -> service.create(new CreateImportJobCommand(
                OPERATOR_ID, TEMPLATE_ID, null, "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[]{1}
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("作业保存失败");

        verify(contentService).discardContent("import/job/source");
    }

    private ImportExportTemplateRecord template() {
        return new ImportExportTemplateRecord(
                TEMPLATE_ID, "学生导入模板", TemplateType.IMPORT, "STUDENT", "V1",
                1874244142494646899L, false, "ID:" + TEMPLATE_ID,
                ImportExportTemplateStatus.ENABLED, 0L, null, null,
                "template.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 128L
        );
    }

    private List<ImportExportTemplateFieldRecord> fields() {
        return List.of(
                new ImportExportTemplateFieldRecord(
                        1L, TEMPLATE_ID, "STUDENT_CODE", "学生账号", ImportTemplateFieldDataType.TEXT,
                        true, 8, null, 10, null, null),
                new ImportExportTemplateFieldRecord(
                        2L, TEMPLATE_ID, "STUDENT_STATUS", "学生状态", ImportTemplateFieldDataType.TEXT,
                        true, 20, "STUDENT_STATUS", 20, null, null)
        );
    }

    private ManagedFile storedFile() {
        return new ManagedFile(
                FILE_ID, "import/job/source", "students.xlsx", "xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 3L,
                OPERATOR_ID, "IMPORT_JOB", "IMPORT_VALIDATION", "0".repeat(64),
                FileStatus.AVAILABLE
        );
    }
}
