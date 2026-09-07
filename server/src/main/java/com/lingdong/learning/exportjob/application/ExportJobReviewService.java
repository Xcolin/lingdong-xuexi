package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.audit.application.SystemTask;
import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 将系统审核结论和敏感导出作业状态置于同一事务。 */
@Service
public class ExportJobReviewService {
    private final ExportJobAccessService accessService;
    private final ExportJobMapper jobMapper;
    private final SystemTaskMapper taskMapper;
    private final SystemTaskApplicationService taskService;
    private final ExportJobEventService eventService;
    private final Clock clock;

    public ExportJobReviewService(
            ExportJobAccessService accessService,
            ExportJobMapper jobMapper,
            SystemTaskMapper taskMapper,
            SystemTaskApplicationService taskService,
            ExportJobEventService eventService,
            Clock clock
    ) {
        this.accessService = accessService;
        this.jobMapper = jobMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.eventService = eventService;
        this.clock = clock;
    }

    @Transactional
    public ExportJobRecord approve(Long taskId, Long reviewerId, String comment) {
        ReviewContext context = requireReview(taskId, reviewerId);
        String normalized = optional(comment, 500);
        SystemTask approved = taskService.approve(taskId, reviewerId, normalized);
        if (approved.status() != SystemTaskStatus.APPROVED) {
            throw new IllegalStateException("系统任务未进入已批准状态");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (jobMapper.queueAfterReview(context.job().id(), context.job().versionNo(), now) != 1) {
            throw new IllegalStateException("敏感导出审核状态已变化");
        }
        eventService.record(context.job().id(), ExportJobEventType.APPROVED,
                reviewerId, "系统审核已批准");
        return requireUpdated(context.job().id());
    }

    @Transactional
    public ExportJobRecord reject(Long taskId, Long reviewerId, String comment) {
        String normalized = required(comment, "审批意见", 500);
        ReviewContext context = requireReview(taskId, reviewerId);
        SystemTask rejected = taskService.reject(taskId, reviewerId, normalized);
        if (rejected.status() != SystemTaskStatus.REJECTED) {
            throw new IllegalStateException("系统任务未进入已驳回状态");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (jobMapper.rejectAfterReview(context.job().id(), context.job().versionNo(), now) != 1) {
            throw new IllegalStateException("敏感导出审核状态已变化");
        }
        eventService.record(context.job().id(), ExportJobEventType.REJECTED,
                reviewerId, "系统审核已驳回");
        return requireUpdated(context.job().id());
    }

    private ReviewContext requireReview(Long taskId, Long reviewerId) {
        accessService.requireSensitiveReview(reviewerId);
        SystemTask task = taskId == null ? null : taskMapper.findById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("系统任务不存在：" + taskId);
        }
        if (task.type() != SystemTaskType.SENSITIVE_DATA_EXPORT) {
            throw new IllegalStateException("该系统任务不是敏感导出任务");
        }
        ExportJobRecord job = jobMapper.findBySystemTaskId(taskId);
        if (job == null || !taskId.equals(job.systemTaskId())
                || job.exportType() != ExportJobType.IAM_CHANGE_AUDIT
                || !Boolean.TRUE.equals(job.sensitive())) {
            throw new IllegalStateException("系统任务与敏感导出作业不匹配");
        }
        if (task.submittedBy().equals(reviewerId)) {
            throw new IllegalStateException("系统审核员不得审批自己提交的任务");
        }
        if (task.status() != SystemTaskStatus.PENDING_REVIEW
                || job.status() != ExportJobStatus.PENDING_REVIEW) {
            throw new IllegalStateException("仅待审核的敏感导出可以审批");
        }
        return new ReviewContext(task, job);
    }

    private ExportJobRecord requireUpdated(Long jobId) {
        ExportJobRecord updated = jobMapper.findById(jobId);
        if (updated == null) {
            throw new IllegalStateException("导出作业不存在：" + jobId);
        }
        return updated;
    }

    private String required(String value, String field, int maxLength) {
        String normalized = optional(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("审批意见长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private record ReviewContext(SystemTask task, ExportJobRecord job) { }
}
