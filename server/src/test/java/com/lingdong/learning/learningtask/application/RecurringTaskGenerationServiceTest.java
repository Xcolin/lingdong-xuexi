package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskAssignment;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrence;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.domain.LearningTaskTargetType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskRecurrenceMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTargetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecurringTaskGenerationServiceTest {
    private final LearningTaskRecurrenceMapper recurrenceMapper = mock(LearningTaskRecurrenceMapper.class);
    private final LearningTaskMapper taskMapper = mock(LearningTaskMapper.class);
    private final LearningTaskTargetMapper targetMapper = mock(LearningTaskTargetMapper.class);
    private final LearningTaskAssignmentMapper assignmentMapper = mock(LearningTaskAssignmentMapper.class);
    private final LearningTaskTargetExpansionService expansionService = mock(LearningTaskTargetExpansionService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final RecurringTaskGenerationService service = new RecurringTaskGenerationService(
            recurrenceMapper, taskMapper, targetMapper, assignmentMapper, expansionService, idGenerator);

    private final LocalDate today = LocalDate.of(2026, 8, 3);
    private final LearningTask task = new LearningTask(
            22L, LearningTaskSourceType.ORGANIZATION, 33L, 44L, "每日阅读",
            2, 20, 30, LocalDate.of(2026, 8, 1), "READING", null,
            55L, 72, true, today, LearningTaskStatus.PUBLISHED,
            null, null, null, LearningTaskRecurrenceStatus.ACTIVE);
    private final LearningTaskRecurrence recurrence = new LearningTaskRecurrence(
            11L, 22L, "DAILY", LocalDate.of(2026, 8, 1), today,
            LocalDate.of(2026, 8, 2), LearningTaskRecurrenceStatus.ACTIVE,
            null, null, 0, null, null);
    private final List<LearningTaskTarget> targets = List.of(
            new LearningTaskTarget(66L, 22L, LearningTaskTargetType.ORGANIZATION, 33L, null));

    @BeforeEach
    void setUp() {
        when(recurrenceMapper.findByIdForUpdate(11L)).thenReturn(recurrence);
        when(taskMapper.findById(22L)).thenReturn(task);
        when(targetMapper.findByTaskId(22L)).thenReturn(targets);
        when(expansionService.expand(targets)).thenReturn(List.of(101L, 102L));
        when(assignmentMapper.findStudentIdsByTaskAndDate(22L, LocalDate.of(2026, 8, 2)))
                .thenReturn(List.of(101L));
        when(assignmentMapper.findStudentIdsByTaskAndDate(22L, today)).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn(9001L, 9002L, 9003L);
        when(assignmentMapper.insertBatch(anyList())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
        when(recurrenceMapper.advanceGeneration(
                11L, LocalDate.of(2026, 8, 4), LearningTaskRecurrenceStatus.COMPLETED, 0))
                .thenReturn(1);
    }

    @Test
    void catchesUpMissingDatesSkipsExistingStudentsAndCompletesAtEndDate() {
        RecurringTaskGenerationResult result = service.generate(11L, today);

        assertThat(result).isEqualTo(new RecurringTaskGenerationResult(11L, 2, 3, true));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LearningTaskAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(assignmentMapper, times(2)).insertBatch(captor.capture());
        assertThat(captor.getAllValues().get(0))
                .extracting(LearningTaskAssignment::studentId)
                .containsExactly(102L);
        assertThat(captor.getAllValues().get(1))
                .extracting(LearningTaskAssignment::studentId)
                .containsExactly(101L, 102L);
        assertThat(captor.getAllValues().stream().flatMap(List::stream))
                .allSatisfy(assignment -> assertThat(assignment.dueAt().toLocalTime().toString())
                        .isEqualTo("23:59:59"));
        verify(recurrenceMapper).advanceGeneration(
                11L, LocalDate.of(2026, 8, 4), LearningTaskRecurrenceStatus.COMPLETED, 0);
    }

    @Test
    void advancesWithoutWritingWhenTargetsAreEmpty() {
        when(expansionService.expand(targets)).thenReturn(List.of());

        RecurringTaskGenerationResult result = service.generate(11L, today);

        assertThat(result.generatedDateCount()).isEqualTo(2);
        assertThat(result.generatedAssignmentCount()).isZero();
        verify(assignmentMapper, never()).insertBatch(anyList());
    }

    @Test
    void returnsNoWorkForInactiveOrFuturePlan() {
        LearningTaskRecurrence stopped = new LearningTaskRecurrence(
                11L, 22L, "DAILY", today, null, today,
                LearningTaskRecurrenceStatus.STOPPED, 88L, null, 1, null, null);
        when(recurrenceMapper.findByIdForUpdate(11L)).thenReturn(stopped);

        assertThat(service.generate(11L, today))
                .isEqualTo(new RecurringTaskGenerationResult(11L, 0, 0, false));
        verify(taskMapper, never()).findById(22L);
    }
}
