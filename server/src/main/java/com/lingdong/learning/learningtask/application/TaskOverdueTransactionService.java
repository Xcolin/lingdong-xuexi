package com.lingdong.learning.learningtask.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 为批处理中的每个到期任务建立独立事务边界。 */
@Service
public class TaskOverdueTransactionService {
    private final TaskOverdueService overdueService;

    public TaskOverdueTransactionService(TaskOverdueService overdueService) {
        this.overdueService = overdueService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markNeedsImprovement(Long assignmentId, LocalDateTime cutoff) {
        return overdueService.markNeedsImprovement(assignmentId, cutoff);
    }
}
