package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 校验批量请求并通过独立事务工作服务逐项发布。 */
@Service
public class LearningTaskBatchPublishService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LearningTaskBatchPublishService.class);
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final int MAX_BATCH_SIZE = 100;
    private static final String NEUTRAL_FAILURE_REASON = "任务不可发布，请检查状态或数据范围";

    private final LearningTaskPublishTransactionService transactionService;
    private final FeatureAccessService featureAccessService;

    public LearningTaskBatchPublishService(
            LearningTaskPublishTransactionService transactionService,
            FeatureAccessService featureAccessService
    ) {
        this.transactionService = transactionService;
        this.featureAccessService = featureAccessService;
    }

    public BatchPublishLearningTasksResult publish(
            AuthenticatedUser currentUser, List<Long> taskIds
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        validateTaskIds(taskIds);
        List<BatchPublishLearningTaskItemResult> items = new ArrayList<>(taskIds.size());
        int successCount = 0;
        for (Long taskId : taskIds) {
            try {
                PublishLearningTaskResult result = transactionService.publish(currentUser, taskId);
                items.add(BatchPublishLearningTaskItemResult.success(
                        taskId, result.assignmentCount()));
                successCount++;
            } catch (RuntimeException exception) {
                LOGGER.warn("批量发布中的任务发布失败，taskId={}", taskId, exception);
                items.add(BatchPublishLearningTaskItemResult.failure(
                        taskId, NEUTRAL_FAILURE_REASON));
            }
        }
        return new BatchPublishLearningTasksResult(
                successCount, taskIds.size() - successCount, items);
    }

    private void validateTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new IllegalArgumentException("批量发布任务不能为空");
        }
        if (taskIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次批量发布不能超过 100 个任务");
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long taskId : taskIds) {
            if (taskId == null || taskId <= 0) {
                throw new IllegalArgumentException("任务标识不合法");
            }
            if (!uniqueIds.add(taskId)) {
                throw new IllegalArgumentException("批量发布任务不能重复");
            }
        }
    }
}
