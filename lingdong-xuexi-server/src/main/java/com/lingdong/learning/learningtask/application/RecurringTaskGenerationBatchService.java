package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskRecurrenceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 按计划雪花标识游标分批生成每日任务，并隔离单计划异常。 */
@Service
public class RecurringTaskGenerationBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecurringTaskGenerationBatchService.class);
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final LearningTaskRecurrenceMapper recurrenceMapper;
    private final RecurringTaskGenerationTransactionService transactionService;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;
    private final int batchSize;

    public RecurringTaskGenerationBatchService(
            LearningTaskRecurrenceMapper recurrenceMapper,
            RecurringTaskGenerationTransactionService transactionService,
            FeatureAccessService featureAccessService,
            Clock clock,
            @Value("${lingdong.recurring-task.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("固定任务生成批次大小必须在 1 至 1000 之间");
        }
        this.recurrenceMapper = recurrenceMapper;
        this.transactionService = transactionService;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int processDuePlans() {
        if (!featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return 0;
        }
        LocalDate businessDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        int processed = 0;
        long afterId = 0L;
        List<Long> recurrenceIds;
        do {
            recurrenceIds = recurrenceMapper.findDueIdsAfter(afterId, businessDate, batchSize);
            for (Long recurrenceId : recurrenceIds) {
                afterId = recurrenceId;
                try {
                    transactionService.generate(recurrenceId, businessDate);
                    processed++;
                } catch (RuntimeException exception) {
                    LOGGER.warn("固定任务计划生成失败，已隔离并继续。recurrenceId={}", recurrenceId, exception);
                }
            }
        } while (recurrenceIds.size() == batchSize);
        return processed;
    }
}
