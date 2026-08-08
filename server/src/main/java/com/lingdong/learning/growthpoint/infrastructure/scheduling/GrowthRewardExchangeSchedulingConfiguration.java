package com.lingdong.learning.growthpoint.infrastructure.scheduling;

import com.lingdong.learning.growthpoint.application.GrowthRewardExchangeCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按配置启用奖励兑换到期清理，不影响接口内的同步截止校验。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.reward-exchange.cleanup",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class GrowthRewardExchangeSchedulingConfiguration {
    private final GrowthRewardExchangeCleanupService cleanupService;

    public GrowthRewardExchangeSchedulingConfiguration(
            GrowthRewardExchangeCleanupService cleanupService
    ) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            initialDelayString = "${lingdong.reward-exchange.cleanup.initial-delay-ms:60000}",
            fixedDelayString = "${lingdong.reward-exchange.cleanup.fixed-delay-ms:60000}"
    )
    public void cleanup() {
        cleanupService.processAll();
    }
}
