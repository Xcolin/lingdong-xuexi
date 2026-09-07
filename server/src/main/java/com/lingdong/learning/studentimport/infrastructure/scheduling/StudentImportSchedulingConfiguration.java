package com.lingdong.learning.studentimport.infrastructure.scheduling;

import com.lingdong.learning.studentimport.application.StudentImportBatchService;
import com.lingdong.learning.studentimport.application.StudentImportCredentialCleanupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 按配置周期领取学员导入任务并清理到期凭证。 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        prefix = "lingdong.student-import",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StudentImportSchedulingConfiguration {
    private final StudentImportBatchService batchService;
    private final StudentImportCredentialCleanupService cleanupService;

    public StudentImportSchedulingConfiguration(
            StudentImportBatchService batchService,
            StudentImportCredentialCleanupService cleanupService
    ) {
        this.batchService = batchService;
        this.cleanupService = cleanupService;
    }

    @Scheduled(cron = "${lingdong.student-import.cron:0 */1 * * * *}", zone = "Asia/Shanghai")
    public void processQueuedImports() {
        batchService.processAvailable();
    }

    @Scheduled(cron = "${lingdong.student-import.cleanup-cron:0 */10 * * * *}", zone = "Asia/Shanghai")
    public void cleanupExpiredCredentials() {
        cleanupService.cleanupExpired();
    }
}
