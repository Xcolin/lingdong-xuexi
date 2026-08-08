package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 每日按实例雪花标识分批自动顺延昨日待优化任务。 */
@Service
public class TaskAutoDeferBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskAutoDeferBatchService.class);
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskDeferMapper deferMapper;
    private final TaskDeferTransactionService transactionService;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;
    private final int batchSize;

    public TaskAutoDeferBatchService(
            TaskDeferMapper deferMapper,
            TaskDeferTransactionService transactionService,
            FeatureAccessService featureAccessService,
            Clock clock,
            @Value("${lingdong.task-defer.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("任务自动顺延批次大小必须在 1 至 1000 之间");
        }
        this.deferMapper = deferMapper;
        this.transactionService = transactionService;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int processYesterdayAssignments() {
        if (!featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return 0;
        }
        LocalDate targetDate = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        LocalDate sourceDate = targetDate.minusDays(1);
        int processed = 0;
        long afterId = 0L;
        List<Long> assignmentIds;
        do {
            assignmentIds = deferMapper.findAutomaticCandidateIdsAfter(afterId, sourceDate, batchSize);
            for (Long assignmentId : assignmentIds) {
                afterId = assignmentId;
                try {
                    if (transactionService.deferAutomatically(assignmentId, targetDate) != null) {
                        processed++;
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("任务自动顺延失败，已隔离并继续。assignmentId={}", assignmentId, exception);
                }
            }
        } while (assignmentIds.size() == batchSize);
        return processed;
    }
}
