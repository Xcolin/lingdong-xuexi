package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 按实例雪花标识游标分批处理到期任务，并隔离单实例异常。 */
@Service
public class TaskOverdueBatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOverdueBatchService.class);
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskOverdueTransactionService transactionService;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;
    private final int batchSize;

    public TaskOverdueBatchService(
            LearningTaskAssignmentMapper assignmentMapper,
            TaskOverdueTransactionService transactionService,
            FeatureAccessService featureAccessService,
            Clock clock,
            @Value("${lingdong.task-overdue.batch-size:100}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("任务待优化批次大小必须在 1 至 1000 之间");
        }
        this.assignmentMapper = assignmentMapper;
        this.transactionService = transactionService;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    public int processOverdueAssignments() {
        if (!featureAccessService.isEnabled(FEATURE_CODE, null)) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        int processed = 0;
        long afterId = 0L;
        List<Long> assignmentIds;
        do {
            assignmentIds = assignmentMapper.findOverdueIdsAfter(afterId, cutoff, batchSize);
            for (Long assignmentId : assignmentIds) {
                afterId = assignmentId;
                try {
                    if (transactionService.markNeedsImprovement(assignmentId, cutoff)) {
                        processed++;
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("任务转待优化失败，已隔离并继续。assignmentId={}", assignmentId, exception);
                }
            }
        } while (assignmentIds.size() == batchSize);
        return processed;
    }
}
