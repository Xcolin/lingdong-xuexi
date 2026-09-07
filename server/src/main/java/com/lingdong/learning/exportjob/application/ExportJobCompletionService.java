package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.audit.application.SystemTaskApplicationService;
import com.lingdong.learning.exportjob.domain.ExportJobEventType;
import com.lingdong.learning.exportjob.domain.ExportJobRecord;
import com.lingdong.learning.exportjob.domain.ExportJobStatus;
import com.lingdong.learning.exportjob.infrastructure.persistence.ExportJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 原子提交成功终态，并在敏感导出成功后生效系统任务。 */
@Service
public class ExportJobCompletionService {
    private final ExportJobMapper jobMapper;
    private final ExportJobEventService eventService;
    private final SystemTaskApplicationService taskService;
    private final Clock clock;

    public ExportJobCompletionService(
            ExportJobMapper jobMapper,
            ExportJobEventService eventService,
            SystemTaskApplicationService taskService,
            Clock clock
    ) {
        this.jobMapper = jobMapper;
        this.eventService = eventService;
        this.taskService = taskService;
        this.clock = clock;
    }

    @Transactional
    public void complete(
            ExportJobRecord job,
            long expectedVersion,
            Long resultFileId,
            long totalRows,
            long processedRows
    ) {
        if (job == null || job.status() != ExportJobStatus.EXPORTING || resultFileId == null) {
            throw new IllegalStateException("只有生成中的导出作业可以完成");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (jobMapper.succeed(job.id(), expectedVersion, resultFileId,
                totalRows, processedRows, now) != 1) {
            throw new IllegalStateException("导出作业成功终态更新冲突：" + job.id());
        }
        eventService.record(job.id(), ExportJobEventType.SUCCEEDED, null, "导出文件生成成功");
        if (Boolean.TRUE.equals(job.sensitive())) {
            if (job.systemTaskId() == null) {
                throw new IllegalStateException("敏感导出缺少系统任务");
            }
            taskService.markEffective(job.systemTaskId());
        }
    }
}
