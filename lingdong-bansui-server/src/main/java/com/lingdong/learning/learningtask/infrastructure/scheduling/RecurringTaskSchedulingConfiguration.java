package com.lingdong.learning.learningtask.infrastructure.scheduling;

import com.lingdong.learning.learningtask.application.RecurringTaskGenerationBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 每日上海时区零点五分触发固定任务实例生成。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.recurring-task",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RecurringTaskSchedulingConfiguration {
    private final RecurringTaskGenerationBatchService batchService;

    public RecurringTaskSchedulingConfiguration(RecurringTaskGenerationBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${lingdong.recurring-task.cron:0 5 0 * * *}", zone = "Asia/Shanghai")
    public void generateDailyAssignments() {
        batchService.processDuePlans();
    }
}
