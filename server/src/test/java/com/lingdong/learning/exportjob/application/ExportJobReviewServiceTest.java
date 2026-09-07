package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.audit.application.ImpactScope;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExportJobReviewServiceTest {
    private static final long REQUESTER_ID = 1874244142494646970L;
    private static final long AUDITOR_ID = 1874244142494646971L;
    private static final long JOB_ID = 1874244142494646972L;
    private static final long TASK_ID = 1874244142494646973L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);

    private ExportJobAccessService accessService;
    private ExportJobMapper jobMapper;
    private SystemTaskMapper taskMapper;
    private SystemTaskApplicationService taskService;
    private ExportJobEventService eventService;
    private ExportJobReviewService service;

    @BeforeEach
    void setUp() {
        accessService = mock(ExportJobAccessService.class);
        jobMapper = mock(ExportJobMapper.class);
        taskMapper = mock(SystemTaskMapper.class);
        taskService = mock(SystemTaskApplicationService.class);
        eventService = mock(ExportJobEventService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ExportJobReviewService(
                accessService, jobMapper, taskMapper, taskService, eventService, clock);
        when(jobMapper.findBySystemTaskId(TASK_ID)).thenReturn(job());
        when(taskMapper.findById(TASK_ID)).thenReturn(task(SystemTaskStatus.PENDING_REVIEW));
    }

    @Test
    void approvesOnlyMatchingSensitiveTaskAndQueuesJobOnce() {
        when(taskService.approve(TASK_ID, AUDITOR_ID, "同意导出"))
                .thenReturn(task(SystemTaskStatus.APPROVED));
        when(jobMapper.queueAfterReview(JOB_ID, 0L, NOW)).thenReturn(1);
        when(jobMapper.findById(JOB_ID)).thenReturn(job(ExportJobStatus.QUEUED, 1L));

        ExportJobRecord approved = service.approve(TASK_ID, AUDITOR_ID, "同意导出");

        assertThat(approved.status()).isEqualTo(ExportJobStatus.QUEUED);
        verify(accessService).requireSensitiveReview(AUDITOR_ID);
        verify(eventService).record(JOB_ID, ExportJobEventType.APPROVED, AUDITOR_ID, "系统审核已批准");
    }

    @Test
    void rejectsWithRequiredCommentAndDoesNotMarkTaskEffective() {
        assertThatThrownBy(() -> service.reject(TASK_ID, AUDITOR_ID, " "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("审批意见");

        when(taskService.reject(TASK_ID, AUDITOR_ID, "范围过大"))
                .thenReturn(task(SystemTaskStatus.REJECTED));
        when(jobMapper.rejectAfterReview(JOB_ID, 0L, NOW)).thenReturn(1);
        when(jobMapper.findById(JOB_ID)).thenReturn(job(ExportJobStatus.REJECTED, 1L));
        ExportJobRecord rejected = service.reject(TASK_ID, AUDITOR_ID, " 范围过大 ");

        assertThat(rejected.status()).isEqualTo(ExportJobStatus.REJECTED);
        verify(taskService, never()).markEffective(TASK_ID);
        verify(eventService).record(JOB_ID, ExportJobEventType.REJECTED, AUDITOR_ID, "系统审核已驳回");
    }

    @Test
    void rejectsSelfReviewWrongTaskTypeAndChangedStateBeforeMutation() {
        assertThatThrownBy(() -> service.approve(TASK_ID, REQUESTER_ID, "自审"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("自己");

        when(taskMapper.findById(TASK_ID)).thenReturn(new SystemTask(
                TASK_ID, "code", SystemTaskType.CACHE_CLEAR, "缓存清理", "说明",
                ImpactScope.GLOBAL, SystemTaskStatus.PENDING_REVIEW, REQUESTER_ID,
                NOW, null, null, null, NOW, NOW));
        assertThatThrownBy(() -> service.approve(TASK_ID, AUDITOR_ID, "同意"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("敏感导出");

        when(taskMapper.findById(TASK_ID)).thenReturn(task(SystemTaskStatus.PENDING_REVIEW));
        when(jobMapper.findBySystemTaskId(TASK_ID)).thenReturn(job(ExportJobStatus.QUEUED, 1L));
        assertThatThrownBy(() -> service.approve(TASK_ID, AUDITOR_ID, "重复"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("待审核");
        verify(taskService, never()).approve(TASK_ID, AUDITOR_ID, "重复");
    }

    private ExportJobRecord job() {
        return job(ExportJobStatus.PENDING_REVIEW, 0L);
    }

    private ExportJobRecord job(ExportJobStatus status, long version) {
        return new ExportJobRecord(
                JOB_ID, "EXP-" + JOB_ID, ExportJobType.IAM_CHANGE_AUDIT,
                1874244142494646974L, "通用报表模板", "V1", REQUESTER_ID,
                null, TASK_ID, "{}", "[]", "{}", "{}", "安全审计复核",
                true, status, version, null, 0L, 0L, null, null, "0".repeat(64),
                NOW.minusHours(1), null, null, null, null, NOW.minusHours(1), NOW.minusHours(1));
    }

    private SystemTask task(SystemTaskStatus status) {
        return new SystemTask(
                TASK_ID, "task-code", SystemTaskType.SENSITIVE_DATA_EXPORT,
                "权限变更日志导出", "安全审计复核", ImpactScope.GLOBAL,
                status, REQUESTER_ID, NOW.minusHours(1), null, null, null,
                NOW.minusHours(1), NOW.minusHours(1));
    }
}
