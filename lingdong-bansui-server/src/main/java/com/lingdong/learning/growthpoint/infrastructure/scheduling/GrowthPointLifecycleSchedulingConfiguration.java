package com.lingdong.learning.growthpoint.infrastructure.scheduling;

import com.lingdong.learning.growthpoint.application.GrowthPointDormancyBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 在上海时区按小时处理沉睡提醒和可用积分清零。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.point-lifecycle",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GrowthPointLifecycleSchedulingConfiguration {
    private final GrowthPointDormancyBatchService batchService;

    public GrowthPointLifecycleSchedulingConfiguration(GrowthPointDormancyBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${lingdong.point-lifecycle.cron:0 10 * * * *}", zone = "Asia/Shanghai")
    public void processDormancy() {
        batchService.processDueStudents();
    }
}
