package com.lingdong.learning.attachment.infrastructure.scheduling;

import com.lingdong.learning.attachment.application.AttachmentRetentionCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按配置周期收敛已退役附件的残留物理内容；不设定保留期限，仅状态机驱动的幂等收敛。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.attachment.cleanup",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AttachmentSchedulingConfiguration {
    private final AttachmentRetentionCleanupService cleanupService;

    public AttachmentSchedulingConfiguration(AttachmentRetentionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${lingdong.attachment.cleanup.cron:0 30 3 * * *}", zone = "Asia/Shanghai")
    public void cleanupResidualRetiredContent() {
        cleanupService.cleanupResidualContent();
    }
}
