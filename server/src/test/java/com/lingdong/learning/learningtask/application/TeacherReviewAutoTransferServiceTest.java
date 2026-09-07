package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerOptionRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerTransferMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewerMapper;
import com.lingdong.learning.teacher.application.TeacherPendingReviewException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherReviewAutoTransferServiceTest {
    private final TaskReviewMapper reviewMapper = mock(TaskReviewMapper.class);
    private final TaskReviewerMapper reviewerMapper = mock(TaskReviewerMapper.class);
    private final LearningTaskAssignmentMapper assignmentMapper = mock(LearningTaskAssignmentMapper.class);
    private final ReviewerTransferMapper transferMapper = mock(ReviewerTransferMapper.class);
    private final TaskAssignmentEventMapper eventMapper = mock(TaskAssignmentEventMapper.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-09-06T08:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final TeacherReviewAutoTransferService service = new TeacherReviewAutoTransferService(
            reviewMapper, reviewerMapper, assignmentMapper, transferMapper, eventMapper,
            idGenerator, clock);
    private final AuthenticatedUser administrator = new AuthenticatedUser(
            8910000000000000941L, 1L, "admin", "机构管理员",
            AuthClientType.WEB, List.of("ORG_ADMIN"));

    @Test
    void transfersClassPendingReviewsToTheFirstQualifiedReplacement() {
        long teacherId = 8910000000000000942L;
        long classId = 8910000000000000943L;
        TaskReviewStateRow state = state(8910000000000000944L, teacherId, classId);
        when(reviewMapper.findPendingByReviewerAndClassForUpdate(teacherId, classId))
                .thenReturn(List.of(state));
        when(reviewerMapper.findOptions(
                state.sourceType(), state.sourceOrganizationId(), state.studentId(), teacherId))
                .thenReturn(List.of(new ReviewerOptionRow(8910000000000000945L, "接替教师")));
        when(assignmentMapper.transferReviewer(
                state.assignmentId(), teacherId, 8910000000000000945L,
                state.versionNo(), java.time.LocalDateTime.now(clock))).thenReturn(1);
        when(transferMapper.insert(any())).thenReturn(1);
        when(eventMapper.insert(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(
                8910000000000000946L, 8910000000000000947L);

        service.transferForClass(administrator, teacherId, classId);

        verify(assignmentMapper).transferReviewer(
                state.assignmentId(), teacherId, 8910000000000000945L,
                state.versionNo(), java.time.LocalDateTime.now(clock));
        verify(transferMapper).insert(any());
        verify(eventMapper).insert(any());
    }

    @Test
    void rejectsTeacherChangeWhenNoQualifiedReplacementExists() {
        long teacherId = 8910000000000000951L;
        TaskReviewStateRow state = state(
                8910000000000000952L, teacherId, 8910000000000000953L);
        when(reviewMapper.findPendingByReviewerForUpdate(teacherId)).thenReturn(List.of(state));
        when(reviewerMapper.findOptions(
                state.sourceType(), state.sourceOrganizationId(), state.studentId(), teacherId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.transferAll(administrator, teacherId))
                .isInstanceOf(TeacherPendingReviewException.class);
        verify(assignmentMapper, never()).transferReviewer(any(), any(), any(), anyInt(), any());
    }

    private TaskReviewStateRow state(long assignmentId, long teacherId, long classId) {
        return new TaskReviewStateRow(
                assignmentId, 8910000000000000961L, 8910000000000000962L, 20,
                LearningTaskSourceType.TEACHER, classId, LocalDate.of(2026, 9, 6),
                TaskAssignmentStatus.PENDING_REVIEW, teacherId, 3);
    }
}
