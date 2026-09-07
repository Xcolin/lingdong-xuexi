package com.lingdong.learning.importjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.importjob.application.validation.ImportFieldMappingSnapshot;
import com.lingdong.learning.importjob.domain.ImportJobRecord;
import com.lingdong.learning.importjob.domain.ImportJobRowResultRecord;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobMapper;
import com.lingdong.learning.importjob.infrastructure.persistence.ImportJobRowResultMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 查询导入校验作业，并在每次读取内容前执行实时对象级鉴权。 */
@Service
public class ImportJobQueryService {
    private final ImportJobMapper jobMapper;
    private final ImportJobRowResultMapper rowMapper;
    private final ImportJobAccessService accessService;
    private final OrganizationDataScopeService dataScopeService;
    private final ManagedAttachmentContentService contentService;
    private final ObjectMapper objectMapper;

    public ImportJobQueryService(
            ImportJobMapper jobMapper,
            ImportJobRowResultMapper rowMapper,
            ImportJobAccessService accessService,
            OrganizationDataScopeService dataScopeService,
            ManagedAttachmentContentService contentService,
            ObjectMapper objectMapper
    ) {
        this.jobMapper = jobMapper;
        this.rowMapper = rowMapper;
        this.accessService = accessService;
        this.dataScopeService = dataScopeService;
        this.contentService = contentService;
        this.objectMapper = objectMapper;
    }

    public ImportJobPage findPage(ImportJobQuery query) {
        validateQuery(query);
        accessService.requireReadPermission(query.operatorId());
        boolean systemAdministrator = accessService.isSystemAdministrator(query.operatorId());
        Long ownerId = systemAdministrator ? null : query.operatorId();
        List<Long> organizationIds = systemAdministrator ? List.of()
                : dataScopeService.findAccessibleOrganizations(query.operatorId()).stream()
                .map(organization -> organization.id())
                .toList();
        int offset = (query.page() - 1) * query.pageSize();
        List<ImportJobView> items = jobMapper.findPage(
                        query, ownerId, organizationIds, offset, query.pageSize()).stream()
                .map(ImportJobView::from)
                .toList();
        long total = jobMapper.count(query, ownerId, organizationIds);
        return new ImportJobPage(items, query.page(), query.pageSize(), total);
    }

    public ImportJobDetailView findDetail(Long operatorId, Long jobId) {
        ImportJobRecord job = requireReadable(operatorId, jobId);
        return new ImportJobDetailView(
                ImportJobView.from(job), deserializeSnapshot(job.fieldMappingSnapshot()));
    }

    public ImportJobRowResultPage findErrors(
            Long operatorId,
            Long jobId,
            int page,
            int pageSize
    ) {
        ImportJobRecord job = requireReadable(operatorId, jobId);
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        List<ImportJobRowErrorView> items = rowMapper
                .findByJobId(job.id(), true, offset, pageSize).stream()
                .map(this::toErrorView)
                .toList();
        return new ImportJobRowResultPage(
                items, page, pageSize, rowMapper.countByJobId(job.id(), true));
    }

    public AttachmentContentView readSource(Long operatorId, Long jobId) {
        ImportJobRecord job = requireReadable(operatorId, jobId);
        return contentService.read(job.sourceFileId());
    }

    public AttachmentContentView readErrorFile(Long operatorId, Long jobId) {
        ImportJobRecord job = requireReadable(operatorId, jobId);
        if (job.errorFileId() == null) {
            throw new ResourceNotFoundException("导入校验作业尚无错误文件：" + jobId);
        }
        return contentService.read(job.errorFileId());
    }

    private ImportJobRecord requireReadable(Long operatorId, Long jobId) {
        if (operatorId == null || jobId == null) {
            throw new IllegalArgumentException("操作人和导入校验作业标识不能为空");
        }
        ImportJobRecord job = jobMapper.findById(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("导入校验作业不存在：" + jobId);
        }
        accessService.requireRead(operatorId, job);
        return job;
    }

    private List<ImportFieldMappingSnapshot> deserializeSnapshot(String value) {
        try {
            return List.copyOf(objectMapper.readValue(value, new TypeReference<>() { }));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导入字段映射快照无法解析", exception);
        }
    }

    private ImportJobRowErrorView toErrorView(ImportJobRowResultRecord row) {
        return new ImportJobRowErrorView(
                row.id(), row.rowNumber(), row.status(), row.errorSummary(), row.createdAt());
    }

    private void validateQuery(ImportJobQuery query) {
        if (query == null || query.operatorId() == null) {
            throw new IllegalArgumentException("导入校验作业查询请求和操作人不能为空");
        }
        validatePage(query.page(), query.pageSize());
        if (query.jobCode() != null && query.jobCode().length() > 64) {
            throw new IllegalArgumentException("作业编码查询条件长度不能超过64个字符");
        }
        if (query.queuedFrom() != null && query.queuedTo() != null
                && query.queuedFrom().isAfter(query.queuedTo())) {
            throw new IllegalArgumentException("排队开始时间不能晚于结束时间");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法，单页数量范围为1至100");
        }
    }
}
