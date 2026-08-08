package com.lingdong.learning.learningtask.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 为自动顺延批处理中的每个任务建立独立事务边界。 */
@Service
public class TaskDeferTransactionService {
    private final TaskDeferService deferService;

    public TaskDeferTransactionService(TaskDeferService deferService) {
        this.deferService = deferService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskDeferResult deferAutomatically(Long assignmentId, LocalDate targetDate) {
        return deferService.deferAutomatically(assignmentId, targetDate);
    }
}
