package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskDeferHistory;
import com.lingdong.learning.learningtask.domain.TaskDeferType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferHistoryMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskDeferServiceTest {
    private final TaskDeferMapper deferMapper = mock(TaskDeferMapper.class);
    private final LearningTaskMapper taskMapper = mock(LearningTaskMapper.class);
    private final LearningTaskTagMapper tagMapper = mock(LearningTaskTagMapper.class);
    private final LearningTaskAssignmentMapper assignmentMapper = mock(LearningTaskAssignmentMapper.class);
    private final TaskDeferHistoryMapper historyMapper = mock(TaskDeferHistoryMapper.class);
    private final ParentStudentMapper parentStudentMapper = mock(ParentStudentMapper.class);
    private final OrganizationDataScopeService organizationDataScopeService = mock(OrganizationDataScopeService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-08T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final TaskDeferService service = new TaskDeferService(
            deferMapper, taskMapper, tagMapper, assignmentMapper, historyMapper,
            parentStudentMapper, organizationDataScopeService, featureAccessService,
            idGenerator, clock);
    private final AuthenticatedUser parent = new AuthenticatedUser(
            44L, 55L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));

    @BeforeEach
    void setUp() {
        when(idGenerator.nextId()).thenReturn(
                9000000000000000001L, 9000000000000000002L,
                9000000000000000003L, 9000000000000000004L);
        when(taskMapper.insertDeferredCopy(any(), any(), any())).thenReturn(1);
        when(tagMapper.findCodesByTaskId(any())).thenReturn(List.of("DAILY", "READING"));
        when(tagMapper.insert(any())).thenReturn(1);
        when(assignmentMapper.deferAssignment(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(1);
        when(historyMapper.insert(any())).thenReturn(1);
        when(parentStudentMapper.existsActivePrimaryByParentAndStudent(44L, 22L)).thenReturn(true);
    }

    @Test
    void automaticallyDefersYesterdayTaskAndPreservesItsConfigurationThroughClone() {
        LocalDate sourceDate = LocalDate.of(2026, 8, 7);
        LocalDate targetDate = LocalDate.of(2026, 8, 8);
        when(deferMapper.findStateForUpdate(11L)).thenReturn(state(
                TaskAssignmentStatus.NEEDS_IMPROVEMENT, sourceDate, null));

        TaskDeferResult result = service.deferAutomatically(11L, targetDate);

        assertThat(result.status()).isEqualTo(TaskAssignmentStatus.PENDING_CLAIM.name());
        assertThat(result.deferType()).isEqualTo(TaskDeferType.AUTO);
        assertThat(result.targetDate()).isEqualTo(targetDate);
        verify(taskMapper).insertDeferredCopy(9000000000000000001L, 33L, targetDate);
        ArgumentCaptor<TaskDeferHistory> historyCaptor = ArgumentCaptor.forClass(TaskDeferHistory.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertThat(historyCaptor.getValue().operatorUserId()).isNull();
        assertThat(historyCaptor.getValue().sourceScheduledDate()).isEqualTo(sourceDate);
        assertThat(historyCaptor.getValue().targetScheduledDate()).isEqualTo(targetDate);
    }

    @Test
    void manuallyDefersWithinSevenDaysAndMarksAssignmentToSkipAutomaticProcessing() {
        LocalDate sourceDate = LocalDate.of(2026, 8, 7);
        LocalDate targetDate = LocalDate.of(2026, 8, 15);
        when(deferMapper.findStateForUpdate(11L)).thenReturn(state(
                TaskAssignmentStatus.NEEDS_IMPROVEMENT, sourceDate, null));

        TaskDeferResult result = service.deferManually(parent, 11L, targetDate);

        assertThat(result.deferType()).isEqualTo(TaskDeferType.MANUAL);
        verify(assignmentMapper).deferAssignment(
                11L, 9000000000000000001L, TaskAssignmentStatus.NEEDS_IMPROVEMENT.name(),
                TaskAssignmentStatus.PENDING_CLAIM.name(), targetDate,
                targetDate.atTime(23, 59, 59), TaskDeferType.MANUAL.name(), 44L, 0);
    }

    @Test
    void rejectsManualTargetBeyondSevenDays() {
        assertThatThrownBy(() -> service.deferManually(
                parent, 11L, LocalDate.of(2026, 8, 16)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("顺延目标日期必须在未来 1 至 7 天内");

        verify(deferMapper, never()).findStateForUpdate(11L);
    }

    @Test
    void skipsAutomaticProcessingAfterManualDefer() {
        when(deferMapper.findStateForUpdate(11L)).thenReturn(state(
                TaskAssignmentStatus.NEEDS_IMPROVEMENT,
                LocalDate.of(2026, 8, 7), TaskDeferType.MANUAL));

        assertThat(service.deferAutomatically(11L, LocalDate.of(2026, 8, 8))).isNull();

        verify(taskMapper, never()).insertDeferredCopy(any(), any(), any());
    }

    @Test
    void manualDeferOverridesUnclaimedAutomaticDate() {
        when(deferMapper.findStateForUpdate(11L)).thenReturn(state(
                TaskAssignmentStatus.PENDING_CLAIM,
                LocalDate.of(2026, 8, 8), TaskDeferType.AUTO));

        TaskDeferResult result = service.deferManually(
                parent, 11L, LocalDate.of(2026, 8, 10));

        assertThat(result.deferType()).isEqualTo(TaskDeferType.MANUAL);
        assertThat(result.targetDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    private TaskDeferStateRow state(
            TaskAssignmentStatus status, LocalDate scheduledDate, TaskDeferType lastDeferType
    ) {
        return new TaskDeferStateRow(
                11L, 33L, 22L, LearningTaskSourceType.FAMILY, null, 44L,
                status, 0, scheduledDate, lastDeferType, 0);
    }
}
