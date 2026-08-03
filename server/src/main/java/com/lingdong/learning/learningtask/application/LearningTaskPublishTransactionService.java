package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 为批量发布中的每个任务建立独立事务边界。 */
@Service
public class LearningTaskPublishTransactionService {
    private final LearningTaskPublishService publishService;

    public LearningTaskPublishTransactionService(LearningTaskPublishService publishService) {
        this.publishService = publishService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PublishLearningTaskResult publish(AuthenticatedUser currentUser, Long taskId) {
        return publishService.publish(currentUser, taskId);
    }
}
