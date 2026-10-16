package com.lingdong.learning.student.infrastructure.scheduling;
import com.lingdong.learning.student.application.transfer.TransferApplicationBatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Explicit rollout switch; enable only after isolated execution verification. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix="lingdong.student-transfer",name="scheduling-enabled",havingValue="true",matchIfMissing=false)
public class TransferApplicationSchedulingConfiguration {
 private final TransferApplicationBatchService batch;
 public TransferApplicationSchedulingConfiguration(TransferApplicationBatchService batch){this.batch=batch;}
 @Scheduled(cron="${lingdong.student-transfer.cron:0 * * * * *}",zone="Asia/Shanghai")
 public void executeDue(){batch.processDue();}
}
