package com.lingdong.learning.learningtask.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 为批处理中的每个固定任务计划建立独立事务边界。 */
@Service
public class RecurringTaskGenerationTransactionService {
    private final RecurringTaskGenerationService generationService;

    public RecurringTaskGenerationTransactionService(RecurringTaskGenerationService generationService) {
        this.generationService = generationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecurringTaskGenerationResult generate(Long recurrenceId, LocalDate businessDate) {
        return generationService.generate(recurrenceId, businessDate);
    }
}
