package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskAssignment;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrence;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskRecurrenceMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTargetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 在同一事务内锁定计划并补齐截至业务日的学生任务实例。 */
@Service
public class RecurringTaskGenerationService {
    private static final String INITIAL_STATUS = "PENDING_CLAIM";
    private static final LocalTime DUE_TIME = LocalTime.of(23, 59, 59);

    private final LearningTaskRecurrenceMapper recurrenceMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningTaskTargetMapper targetMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final LearningTaskTargetExpansionService expansionService;
    private final IdGenerator idGenerator;

    public RecurringTaskGenerationService(
            LearningTaskRecurrenceMapper recurrenceMapper,
            LearningTaskMapper taskMapper,
            LearningTaskTargetMapper targetMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            LearningTaskTargetExpansionService expansionService,
            IdGenerator idGenerator
    ) {
        this.recurrenceMapper = recurrenceMapper;
        this.taskMapper = taskMapper;
        this.targetMapper = targetMapper;
        this.assignmentMapper = assignmentMapper;
        this.expansionService = expansionService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public RecurringTaskGenerationResult generate(Long recurrenceId, LocalDate businessDate) {
        LearningTaskRecurrence recurrence = recurrenceMapper.findByIdForUpdate(recurrenceId);
        if (recurrence == null
                || recurrence.status() != LearningTaskRecurrenceStatus.ACTIVE
                || recurrence.nextGenerationDate().isAfter(businessDate)) {
            return new RecurringTaskGenerationResult(recurrenceId, 0, 0, false);
        }
        LearningTask task = requirePublishedRecurringTask(recurrence);
        List<LearningTaskTarget> targets = targetMapper.findByTaskId(task.id());
        List<Long> studentIds = expansionService.expand(targets);

        int generatedDateCount = 0;
        int generatedAssignmentCount = 0;
        LocalDate generationDate = recurrence.nextGenerationDate();
        while (!generationDate.isAfter(businessDate)
                && (recurrence.endDate() == null || !generationDate.isAfter(recurrence.endDate()))) {
            generatedDateCount++;
            generatedAssignmentCount += insertMissingAssignments(task, studentIds, generationDate);
            generationDate = generationDate.plusDays(1);
        }

        boolean completed = recurrence.endDate() != null && generationDate.isAfter(recurrence.endDate());
        LearningTaskRecurrenceStatus nextStatus = completed
                ? LearningTaskRecurrenceStatus.COMPLETED
                : LearningTaskRecurrenceStatus.ACTIVE;
        if (recurrenceMapper.advanceGeneration(
                recurrence.id(), generationDate, nextStatus, recurrence.versionNo()) != 1) {
            throw new IllegalStateException("固定任务计划状态已变化，请重试");
        }
        return new RecurringTaskGenerationResult(
                recurrence.id(), generatedDateCount, generatedAssignmentCount, completed);
    }

    private LearningTask requirePublishedRecurringTask(LearningTaskRecurrence recurrence) {
        LearningTask task = taskMapper.findById(recurrence.taskId());
        if (task == null
                || task.status() != LearningTaskStatus.PUBLISHED
                || !Boolean.TRUE.equals(task.recurrenceEnabled())
                || !"DAILY".equals(recurrence.frequencyType())) {
            throw new IllegalStateException("固定任务计划关联的任务状态无效");
        }
        return task;
    }

    private int insertMissingAssignments(
            LearningTask task, List<Long> studentIds, LocalDate generationDate
    ) {
        Set<Long> existingStudentIds = new HashSet<>(
                assignmentMapper.findStudentIdsByTaskAndDate(task.id(), generationDate));
        List<LearningTaskAssignment> assignments = new ArrayList<>();
        for (Long studentId : studentIds) {
            if (!existingStudentIds.contains(studentId)) {
                assignments.add(new LearningTaskAssignment(
                        idGenerator.nextId(), task.id(), studentId, task.sourceType(),
                        task.sourceOrganizationId(), INITIAL_STATUS, task.reviewerUserId(),
                        generationDate, generationDate.atTime(DUE_TIME)));
            }
        }
        if (assignments.isEmpty()) {
            return 0;
        }
        if (assignmentMapper.insertBatch(assignments) != assignments.size()) {
            throw new IllegalStateException("固定任务学生实例生成不完整");
        }
        return assignments.size();
    }
}
