package com.lingdong.learning.exportjob.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobEventMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 查询本人作业、待审元数据和经过对象级复核的结果文件。 */
@Service
public class ExportJobQueryService {
    private final ExportJobMapper jobMapper;
    private final ExportJobEventMapper eventMapper;
    private final ExportJobAccessService accessService;
    private final ManagedAttachmentContentService contentService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public ExportJobQueryService(
            ExportJobMapper jobMapper,
            ExportJobEventMapper eventMapper,
            ExportJobAccessService accessService,
            ManagedAttachmentContentService contentService,
            UserMapper userMapper,
            ObjectMapper objectMapper
    ) {
        this.jobMapper = jobMapper;
        this.eventMapper = eventMapper;
        this.accessService = accessService;
        this.contentService = contentService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public ExportJobPage findPage(
            Long userId,
            ExportJobType exportType,
            ExportJobStatus status,
            int page,
            int pageSize
    ) {
        validatePage(page, pageSize);
        requireId(userId, "操作人");
        accessService.requireListRead(userId);
        int offset = Math.multiplyExact(page - 1, pageSize);
        return new ExportJobPage(
                jobMapper.findPageByRequester(userId, exportType, status, offset, pageSize)
                        .stream().map(ExportJobView::from).toList(),
                page, pageSize, jobMapper.countByRequester(userId, exportType, status));
    }

    public ExportJobDetailView findDetail(Long userId, Long jobId) {
        ExportJobRecord job = requireOwnerReadable(userId, jobId);
        List<ExportColumnSnapshot> columns = readColumns(job.columnSnapshot());
        String scopeSummary = job.studentId() == null
                ? "全局权限变更日志" : "学生积分明细（对象标识：" + job.studentId() + "）";
        return new ExportJobDetailView(
                ExportJobView.from(job), columns, scopeSummary,
                eventMapper.findByJobId(job.id()).stream().map(ExportJobEventView::from).toList());
    }

    public AttachmentContentView download(Long userId, Long jobId) {
        ExportJobRecord job = requireJob(jobId);
        accessService.requireOwnerDownload(userId, job);
        if (job.status() != ExportJobStatus.SUCCEEDED || job.resultFileId() == null) {
            throw new ResourceNotFoundException("导出结果文件不存在：" + jobId);
        }
        return contentService.read(job.resultFileId());
    }

    public ExportJobReviewPage findPendingReviews(Long reviewerId, int page, int pageSize) {
        validatePage(page, pageSize);
        requireId(reviewerId, "审核人");
        accessService.requireSensitiveReview(reviewerId);
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<ExportJobReviewView> items = jobMapper.findPendingReviews(offset, pageSize).stream()
                .map(job -> new ExportJobReviewView(
                        job.id(), job.systemTaskId(), requesterName(job.requesterId()),
                        job.exportType(), "全局权限变更日志", job.requestReason(), job.requestedAt()))
                .toList();
        return new ExportJobReviewPage(items, page, pageSize, jobMapper.countPendingReviews());
    }

    private ExportJobRecord requireOwnerReadable(Long userId, Long jobId) {
        ExportJobRecord job = requireJob(jobId);
        accessService.requireOwnerRead(userId, job);
        return job;
    }

    private ExportJobRecord requireJob(Long jobId) {
        requireId(jobId, "导出作业");
        ExportJobRecord job = jobMapper.findById(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("导出作业不存在：" + jobId);
        }
        return job;
    }

    private List<ExportColumnSnapshot> readColumns(String value) {
        try {
            return List.copyOf(objectMapper.readValue(value, new TypeReference<>() { }));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导出字段快照无法解析", exception);
        }
    }

    private String requesterName(Long requesterId) {
        User user = userMapper.findById(requesterId);
        return user == null ? "未知申请人" : user.displayName();
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法，单页数量范围为1至100");
        }
    }

    private void requireId(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + "标识必须为正整数");
        }
    }
}
