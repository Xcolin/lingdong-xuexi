package com.lingdong.learning.student.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.ReviewerTransfer;
import com.lingdong.learning.learningtask.infrastructure.persistence.ParentRelationshipTaskReviewRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerTransferMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentRelationshipTaskTransferServiceTest {
    private static final long STUDENT_ID = 8910000000000000301L;
    private static final long OLD_PRIMARY_ID = 8910000000000000302L;
    private static final long NEW_PRIMARY_ID = 8910000000000000303L;
    private static final long ASSIGNMENT_ID = 8910000000000000304L;

    private final TaskReviewMapper taskReviewMapper = mock(TaskReviewMapper.class);
    private final ReviewerTransferMapper transferMapper = mock(ReviewerTransferMapper.class);
    private final AtomicLong ids = new AtomicLong(8910000000000000400L);
    private final IdGenerator idGenerator = ids::incrementAndGet;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-09T06:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final ParentRelationshipTaskTransferService service =
            new ParentRelationshipTaskTransferService(
                    taskReviewMapper, transferMapper, idGenerator, clock);

    @Test
    void transfersOnlyLockedFamilyPendingReviewsAndWritesChineseAudit() {
        when(taskReviewMapper.findFamilyPendingByStudentForUpdate(STUDENT_ID, OLD_PRIMARY_ID))
                .thenReturn(List.of(new ParentRelationshipTaskReviewRow(
                        ASSIGNMENT_ID, OLD_PRIMARY_ID)));
        when(taskReviewMapper.transferFamilyPendingReviewer(
                ASSIGNMENT_ID, OLD_PRIMARY_ID, NEW_PRIMARY_ID, LocalDateTime.now(clock)))
                .thenReturn(1);
        when(transferMapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        int transferred = service.transferPendingFamilyReviews(
                STUDENT_ID, OLD_PRIMARY_ID, NEW_PRIMARY_ID, OLD_PRIMARY_ID);

        assertThat(transferred).isEqualTo(1);
        var captor = forClass(ReviewerTransfer.class);
        verify(transferMapper).insert(captor.capture());
        assertThat(captor.getValue().transferReason()).isEqualTo("主家长关系变更");
        assertThat(captor.getValue().assignmentId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(captor.getValue().fromReviewerUserId()).isEqualTo(OLD_PRIMARY_ID);
        assertThat(captor.getValue().toReviewerUserId()).isEqualTo(NEW_PRIMARY_ID);
    }

    @Test
    void failsWholeTransferWhenConditionalReviewerUpdateLosesRace() {
        when(taskReviewMapper.findFamilyPendingByStudentForUpdate(STUDENT_ID, OLD_PRIMARY_ID))
                .thenReturn(List.of(new ParentRelationshipTaskReviewRow(
                        ASSIGNMENT_ID, OLD_PRIMARY_ID)));
        when(taskReviewMapper.transferFamilyPendingReviewer(
                ASSIGNMENT_ID, OLD_PRIMARY_ID, NEW_PRIMARY_ID, LocalDateTime.now(clock)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.transferPendingFamilyReviews(
                STUDENT_ID, OLD_PRIMARY_ID, NEW_PRIMARY_ID, OLD_PRIMARY_ID))
                .isInstanceOf(IllegalStateException.class);
    }
}
