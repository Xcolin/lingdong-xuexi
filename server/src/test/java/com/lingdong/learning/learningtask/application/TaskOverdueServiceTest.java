package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskPause;
import com.lingdong.learning.learningtask.domain.TaskPauseType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskOverdueStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskPauseMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskOverdueServiceTest {
    private final LearningTaskAssignmentMapper assignmentMapper = mock(LearningTaskAssignmentMapper.class);
    private final TaskPauseMapper pauseMapper = mock(TaskPauseMapper.class);
    private final TaskAssignmentEventMapper eventMapper = mock(TaskAssignmentEventMapper.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final TaskOverdueService service = new TaskOverdueService(
            assignmentMapper, pauseMapper, eventMapper, idGenerator);
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 8, 8, 23, 59, 59);

    @Test
    void marksDueInProgressAssignmentAsNeedsImprovementWithoutPenalty() {
        when(assignmentMapper.findOverdueStateForUpdate(11L, cutoff))
                .thenReturn(new TaskOverdueStateRow(11L, 22L, 0));
        when(assignmentMapper.transitionStatus(
                11L, "IN_PROGRESS", "NEEDS_IMPROVEMENT", 0, cutoff, null)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(9000000000000000001L);
        when(eventMapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        assertThat(service.markNeedsImprovement(11L, cutoff)).isTrue();

        ArgumentCaptor<TaskAssignmentEvent> eventCaptor = ArgumentCaptor.forClass(TaskAssignmentEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(TaskAssignmentEventType.MARKED_NEEDS_IMPROVEMENT);
        assertThat(eventCaptor.getValue().operatorUserId()).isNull();
        assertThat(eventCaptor.getValue().fromStatus()).isEqualTo(TaskAssignmentStatus.IN_PROGRESS);
        assertThat(eventCaptor.getValue().toStatus()).isEqualTo(TaskAssignmentStatus.NEEDS_IMPROVEMENT);
        assertThat(eventCaptor.getValue().reason()).isEqualTo("当日未提交打卡，状态转为待优化");
    }

    @Test
    void skipsAssignmentThatStillHasAnActivePause() {
        when(assignmentMapper.findOverdueStateForUpdate(11L, cutoff))
                .thenReturn(new TaskOverdueStateRow(11L, 22L, 0));
        when(pauseMapper.findActive(11L, cutoff)).thenReturn(new TaskPause(
                33L, 11L, TaskPauseType.EMOTION, 44L,
                cutoff.minusMinutes(10), cutoff.plusMinutes(20), null, null));

        assertThat(service.markNeedsImprovement(11L, cutoff)).isFalse();

        verify(assignmentMapper, never()).transitionStatus(
                11L, "IN_PROGRESS", "NEEDS_IMPROVEMENT", 0, cutoff, null);
        verify(eventMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void repeatedProcessingReturnsNoWork() {
        when(assignmentMapper.findOverdueStateForUpdate(11L, cutoff)).thenReturn(null);

        assertThat(service.markNeedsImprovement(11L, cutoff)).isFalse();

        verify(eventMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }
}
