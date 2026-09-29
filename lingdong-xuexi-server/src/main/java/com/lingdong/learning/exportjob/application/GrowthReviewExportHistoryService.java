package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.ManagedAttachmentContentService;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import com.lingdong.learning.growthpoint.application.GrowthReviewExportPreparationService.Prepared;
import org.springframework.stereotype.Service;
import java.util.List;

/** 复盘导出历史仅向仍有学生读取资格的原申请人开放，不依赖新导出开关。 */
@Service
public class GrowthReviewExportHistoryService {
    private final ExportJobMapper jobs;
    private final GrowthReviewExportAccessService access;
    private final FileRelationMapper relations;
    private final ManagedAttachmentContentService contents;

    public GrowthReviewExportHistoryService(ExportJobMapper jobs, GrowthReviewExportAccessService access,
            FileRelationMapper relations, ManagedAttachmentContentService contents) {
        this.jobs = jobs;
        this.access = access;
        this.relations = relations;
        this.contents = contents;
    }

    public ExportJobPage findPage(AuthenticatedUser user, Long studentId, ExportJobStatus status,
            int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法，单页数量范围为1至100");
        }
        int offset;
        try { offset = Math.multiplyExact(page - 1, pageSize); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("分页范围过大", exception); }
        Long requesterId = user == null ? null : user.userId();
        access.requireHistoricalRead(user, new Prepared(requesterId, studentId, List.of()));
        // 学生和申请人条件必须在数据库分页之前生效，避免泄漏其他孩子的记录及总数。
        return new ExportJobPage(jobs.findReviewPageByRequesterAndStudent(requesterId, studentId, status, offset, pageSize)
                .stream().map(ExportJobView::from).toList(), page, pageSize,
                jobs.countReviewsByRequesterAndStudent(requesterId, studentId, status));
    }

    public ExportJobView findDetail(AuthenticatedUser user, Long jobId) {
        return ExportJobView.from(requireReadable(user, jobId));
    }

    public AttachmentContentView download(AuthenticatedUser user, Long jobId) {
        ExportJobRecord job = requireReadable(user, jobId);
        if (job.status() != ExportJobStatus.SUCCEEDED || job.resultFileId() == null
                || relations.countActiveExportResult(job.resultFileId(), job.id(), job.requesterId()) == 0) {
            throw new ResourceNotFoundException("复盘导出结果文件不存在或已失效");
        }
        return contents.read(job.resultFileId());
    }

    private ExportJobRecord requireReadable(AuthenticatedUser user, Long jobId) {
        if (jobId == null || jobId <= 0) { throw new IllegalArgumentException("导出作业标识必须为正整数"); }
        ExportJobRecord job = jobs.findById(jobId);
        if (job == null || job.exportType() != ExportJobType.GROWTH_REVIEW_PDF || job.studentId() == null
                || !Boolean.FALSE.equals(job.sensitive()) || job.systemTaskId() != null) {
            throw new ResourceNotFoundException("复盘导出作业不存在");
        }
        // 历史读取使用作业持久化范围，不重新加载当前复盘或依赖生成模板。
        access.requireHistoricalRead(user, new Prepared(job.requesterId(), job.studentId(), List.of()));
        return job;
    }
}
