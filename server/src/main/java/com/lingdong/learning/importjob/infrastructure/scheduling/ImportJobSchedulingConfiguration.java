package com.lingdong.learning.importjob.infrastructure.scheduling;

import com.lingdong.learning.importjob.application.ImportJobBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按配置周期领取并校验通用导入作业。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.import-validation",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ImportJobSchedulingConfiguration {
    private final ImportJobBatchService batchService;

    public ImportJobSchedulingConfiguration(ImportJobBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(
            cron = "${lingdong.import-validation.cron:0 */1 * * * *}",
            zone = "Asia/Shanghai"
    )
    public void validateQueuedJobs() {
        batchService.processQueuedJobs();
    }
}
