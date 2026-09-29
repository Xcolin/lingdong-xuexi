package com.lingdong.learning.importjob.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobRowResultRecord;
import com.lingdong.learning.importjob.domain.ImportJobRowStatus;
import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobRowResultMapper;
import com.lingdong.learning.organization.domain.Organization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportJobQueryServiceTest {
    private ImportJobMapper jobMapper;
    private ImportJobRowResultMapper rowMapper;
    private ImportJobAccessService accessService;
    private OrganizationDataScopeService dataScopeService;
    private ManagedAttachmentContentService contentService;
    private ImportJobQueryService service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(ImportJobMapper.class);
        rowMapper = mock(ImportJobRowResultMapper.class);
        accessService = mock(ImportJobAccessService.class);
        dataScopeService = mock(OrganizationDataScopeService.class);
        contentService = mock(ManagedAttachmentContentService.class);
        service = new ImportJobQueryService(
                jobMapper, rowMapper, accessService, dataScopeService,
                contentService, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void ordinaryUserPageIsRestrictedToOwnerAndCurrentOrganizationScope() {
        Long userId = 8L;
        Organization school = mock(Organization.class);
        when(school.id()).thenReturn(11L);
        when(accessService.isSystemAdministrator(userId)).thenReturn(false);
        when(dataScopeService.findAccessibleOrganizations(userId)).thenReturn(List.of(school));
        ImportJobQuery query = new ImportJobQuery(
                userId, "IMP", null, null, ImportJobStatus.QUEUED,
                null, null, 1, 20);
        when(jobMapper.findPage(query, userId, List.of(11L), 0, 20)).thenReturn(List.of(job()));
        when(jobMapper.count(query, userId, List.of(11L))).thenReturn(1L);

        ImportJobPage page = service.findPage(query);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        verify(jobMapper).findPage(query, userId, List.of(11L), 0, 20);
    }

    @Test
    void detailChecksCurrentObjectAccessAndDeserializesSnapshot() {
        ImportJobRecord job = job();
        when(jobMapper.findById(job.id())).thenReturn(job);

        ImportJobDetailView detail = service.findDetail(8L, job.id());

        verify(accessService).requireRead(8L, job);
        assertThat(detail.fields()).hasSize(1);
        assertThat(detail.fields().get(0).fieldCode()).isEqualTo("NAME");
    }

    @Test
    void errorRowsArePagedAfterCurrentObjectAccessCheck() {
        ImportJobRecord job = job();
        ImportJobRowResultRecord row = new ImportJobRowResultRecord(
                10L, job.id(), 2, ImportJobRowStatus.INVALID, "姓名不能为空", LocalDateTime.now());
        when(jobMapper.findById(job.id())).thenReturn(job);
        when(rowMapper.findByJobId(job.id(), true, 0, 20)).thenReturn(List.of(row));
        when(rowMapper.countByJobId(job.id(), true)).thenReturn(1L);

        ImportJobRowResultPage page = service.findErrors(8L, job.id(), 1, 20);

        verify(accessService).requireRead(8L, job);
        assertThat(page.items()).extracting(ImportJobRowErrorView::errorSummary)
                .containsExactly("姓名不能为空");
    }

    @Test
    void sourceDownloadUsesJobAuthorizationBeforeReadingManagedContent() {
        ImportJobRecord job = job();
        AttachmentContentView content = new AttachmentContentView(
                "source.xlsx", "application/xlsx", new byte[]{1});
        when(jobMapper.findById(job.id())).thenReturn(job);
        when(contentService.read(job.sourceFileId())).thenReturn(content);

        assertThat(service.readSource(8L, job.id())).isSameAs(content);

        verify(accessService).requireRead(8L, job);
        verify(contentService).read(job.sourceFileId());
    }

    @Test
    void missingJobUsesResourceNotFoundSemantics() {
        when(jobMapper.findById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.findDetail(8L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("导入校验作业不存在：99");
    }

    @Test
    void missingErrorFileUsesResourceNotFoundSemantics() {
        ImportJobRecord job = jobWithoutErrorFile();
        when(jobMapper.findById(job.id())).thenReturn(job);

        assertThatThrownBy(() -> service.readErrorFile(8L, job.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("导入校验作业尚无错误文件：1");
    }

    private ImportJobRecord job() {
        LocalDateTime now = LocalDateTime.now();
        String snapshot = "[{\"fieldCode\":\"NAME\",\"columnName\":\"姓名\","
                + "\"dataType\":\"TEXT\",\"required\":true,\"maxLength\":20,"
                + "\"dictionaryValues\":[],\"sortOrder\":10}]";
        return new ImportJobRecord(
                1L, "IMP-1", 2L, "V1", "模板", snapshot, 3L, 4L,
                8L, 11L, ImportJobStatus.QUEUED, 0L, null, null,
                0, 0, 0, 0, now, null, null, now, now
        );
    }

    private ImportJobRecord jobWithoutErrorFile() {
        ImportJobRecord job = job();
        return new ImportJobRecord(
                job.id(), job.jobCode(), job.templateId(), job.templateVersion(), job.templateName(),
                job.fieldMappingSnapshot(), job.sourceFileId(), null, job.requesterId(),
                job.organizationId(), job.status(), job.versionNo(), job.failureCode(),
                job.failureMessage(), job.totalRows(), job.processedRows(), job.validRows(),
                job.invalidRows(), job.queuedAt(), job.startedAt(), job.completedAt(),
                job.createdAt(), job.updatedAt());
    }
}
