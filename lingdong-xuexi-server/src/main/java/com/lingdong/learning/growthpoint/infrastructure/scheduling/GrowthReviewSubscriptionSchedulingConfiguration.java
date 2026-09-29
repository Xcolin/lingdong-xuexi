package com.lingdong.learning.growthpoint.infrastructure.scheduling;
import com.lingdong.learning.growthpoint.application.GrowthReviewSubscriptionBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 内部待发排程默认不启动，渠道和运维验收完成后再显式启用。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix="lingdong.growth-review-subscription", name="scheduling-enabled", havingValue="true")
public class GrowthReviewSubscriptionSchedulingConfiguration {
    private final GrowthReviewSubscriptionBatchService batch;
    public GrowthReviewSubscriptionSchedulingConfiguration(GrowthReviewSubscriptionBatchService batch) { this.batch=batch; }
    @Scheduled(fixedDelayString="${lingdong.growth-review-subscription.scan-delay-ms:60000}")
    public void reconcile() { batch.process(); }
}
