package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.ReviewerTransfer;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerOptionRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerTransferMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewerMapper;
import com.lingdong.learning.teacher.application.TeacherPendingReviewException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 在教师失效前原子转交其待审核任务，并保留不可变历史。 */
@Service
public class TeacherReviewAutoTransferService {
    private final TaskReviewMapper reviewMapper;
    private final TaskReviewerMapper reviewerMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final ReviewerTransferMapper transferMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public TeacherReviewAutoTransferService(
            TaskReviewMapper reviewMapper,
            TaskReviewerMapper reviewerMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            ReviewerTransferMapper transferMapper,
            TaskAssignmentEventMapper eventMapper,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.reviewMapper = reviewMapper;
        this.reviewerMapper = reviewerMapper;
        this.assignmentMapper = assignmentMapper;
        this.transferMapper = transferMapper;
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public void transferAll(AuthenticatedUser operator, Long teacherUserId) {
        transfer(operator, teacherUserId,
                reviewMapper.findPendingByReviewerForUpdate(teacherUserId),
                "教师账号状态变更自动转交");
    }

    public void transferForClass(
            AuthenticatedUser operator, Long teacherUserId, Long classOrganizationId
    ) {
        transfer(operator, teacherUserId,
                reviewMapper.findPendingByReviewerAndClassForUpdate(
                        teacherUserId, classOrganizationId),
                "教师班级关系解除自动转交");
    }

    private void transfer(
            AuthenticatedUser operator,
            Long teacherUserId,
            List<TaskReviewStateRow> pendingReviews,
            String reason
    ) {
        if (operator == null) {
            throw new IllegalArgumentException("操作人不能为空");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        for (TaskReviewStateRow state : pendingReviews) {
            ReviewerOptionRow replacement = reviewerMapper.findOptions(
                    state.sourceType(), state.sourceOrganizationId(), state.studentId(), teacherUserId)
                    .stream().findFirst().orElseThrow(TeacherPendingReviewException::new);
            requireSingleWrite(assignmentMapper.transferReviewer(
                    state.assignmentId(), teacherUserId, replacement.userId(), state.versionNo(), now));
            requireSingleWrite(transferMapper.insert(new ReviewerTransfer(
                    idGenerator.nextId(), state.assignmentId(), teacherUserId,
                    replacement.userId(), operator.userId(), reason, now)));
            requireSingleWrite(eventMapper.insert(new TaskAssignmentEvent(
                    idGenerator.nextId(), state.assignmentId(),
                    TaskAssignmentEventType.REVIEWER_TRANSFERRED, operator.userId(),
                    state.currentStatus(), state.currentStatus(), reason, null, now)));
        }
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("审核任务状态已变化");
        }
    }
}
