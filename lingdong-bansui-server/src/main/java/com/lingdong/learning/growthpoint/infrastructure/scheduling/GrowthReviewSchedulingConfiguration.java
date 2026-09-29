package com.lingdong.learning.growthpoint.infrastructure.scheduling;

import com.lingdong.learning.growthpoint.application.GrowthReviewBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按中国标准时间调度成长复盘生成和迟到数据回补。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.growth-review",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GrowthReviewSchedulingConfiguration {
    private final GrowthReviewBatchService batchService;

    public GrowthReviewSchedulingConfiguration(GrowthReviewBatchService batchService) {
        this.batchService = batchService;
    }

    @Scheduled(cron = "${lingdong.growth-review.daily-cron:0 0 21 * * *}", zone = "Asia/Shanghai")
    public void generateDaily() {
        batchService.processDaily();
    }

    @Scheduled(cron = "${lingdong.growth-review.weekly-cron:0 0 0 * * MON}", zone = "Asia/Shanghai")
    public void generatePreviousWeek() {
        batchService.processPreviousWeek();
    }

    @Scheduled(cron = "${lingdong.growth-review.monthly-cron:0 0 0 1 * *}", zone = "Asia/Shanghai")
    public void generatePreviousMonth() {
        batchService.processPreviousMonth();
    }

    @Scheduled(cron = "${lingdong.growth-review.backfill-cron:0 15 0 * * *}", zone = "Asia/Shanghai")
    public void backfillPreviousDay() {
        batchService.processPreviousDayBackfill();
    }
}
