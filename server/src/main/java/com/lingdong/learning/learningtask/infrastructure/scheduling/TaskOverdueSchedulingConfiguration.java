package com.lingdong.learning.learningtask.infrastructure.scheduling;

import com.lingdong.learning.learningtask.application.TaskOverdueBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 每日上海时区 23:59:59 处理未提交打卡的进行中任务。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.task-overdue",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TaskOverdueSchedulingConfiguration {
    private final TaskOverdueBatchService batchService;

    public TaskOverdueSchedulingConfiguration(TaskOverdueBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${lingdong.task-overdue.cron:59 59 23 * * *}", zone = "Asia/Shanghai")
    public void markOverdueAssignments() {
        batchService.processOverdueAssignments();
    }
}
