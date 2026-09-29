package com.lingdong.learning.auth.infrastructure.scheduling;

import com.lingdong.learning.auth.application.ParentAccountFinalizationBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按配置周期扫描并终结已结束冷静期的家长注销申请。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.parent-account-finalization",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ParentAccountFinalizationSchedulingConfiguration {
    private final ParentAccountFinalizationBatchService batchService;

    public ParentAccountFinalizationSchedulingConfiguration(
            ParentAccountFinalizationBatchService batchService
    ) {
        this.batchService = batchService;
    }

    @Scheduled(
            cron = "${lingdong.parent-account-finalization.cron:0 */10 * * * *}",
            zone = "Asia/Shanghai"
    )
    public void finalizeDueAccounts() {
        batchService.processDueCancellations();
    }
}
