package com.lingdong.learning.learningtask.infrastructure.scheduling;

import com.lingdong.learning.learningtask.application.TaskAutoDeferBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 每日上海时区零点自动顺延昨日待优化任务。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.task-defer",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TaskDeferSchedulingConfiguration {
    private final TaskAutoDeferBatchService batchService;

    public TaskDeferSchedulingConfiguration(TaskAutoDeferBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${lingdong.task-defer.cron:0 0 0 * * *}", zone = "Asia/Shanghai")
    public void deferYesterdayAssignments() {
        batchService.processYesterdayAssignments();
    }
}
