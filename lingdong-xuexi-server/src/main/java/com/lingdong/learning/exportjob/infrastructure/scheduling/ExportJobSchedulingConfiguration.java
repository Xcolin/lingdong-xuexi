package com.lingdong.learning.exportjob.infrastructure.scheduling;

import com.lingdong.learning.exportjob.application.ExportJobBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按配置周期领取并执行异步导出作业。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.export-job",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ExportJobSchedulingConfiguration {
    private final ExportJobBatchService batchService;

    public ExportJobSchedulingConfiguration(ExportJobBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${lingdong.export-job.cron:0 */1 * * * *}", zone = "Asia/Shanghai")
    public void processQueuedJobs() {
        batchService.processQueuedJobs();
    }
}
