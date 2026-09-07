package com.lingdong.learning.student.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.ReviewerTransfer;
import com.lingdong.learning.learningtask.infrastructure.persistence.ParentRelationshipTaskReviewRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerTransferMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** 主家长变化时原子转交该学生的家庭待审核任务。 */
@Service
public class ParentRelationshipTaskTransferService {
    private static final String TRANSFER_REASON = "主家长关系变更";

    private final TaskReviewMapper taskReviewMapper;
    private final ReviewerTransferMapper transferMapper;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ParentRelationshipTaskTransferService(
            TaskReviewMapper taskReviewMapper,
            ReviewerTransferMapper transferMapper,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.taskReviewMapper = taskReviewMapper;
        this.transferMapper = transferMapper;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public int transferPendingFamilyReviews(
            Long studentId,
            Long fromReviewerUserId,
            Long toReviewerUserId,
            Long operatorUserId
    ) {
        if (fromReviewerUserId == null || toReviewerUserId == null
                || fromReviewerUserId.equals(toReviewerUserId)) {
            throw new IllegalArgumentException("任务审核转交的原审核人与新审核人必须有效且不同");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<ParentRelationshipTaskReviewRow> rows =
                taskReviewMapper.findFamilyPendingByStudentForUpdate(studentId, fromReviewerUserId);
        for (ParentRelationshipTaskReviewRow row : rows) {
            if (taskReviewMapper.transferFamilyPendingReviewer(
                    row.assignmentId(), fromReviewerUserId, toReviewerUserId, now) != 1) {
                throw new IllegalStateException("家庭待审核任务转交失败");
            }
            ReviewerTransfer transfer = new ReviewerTransfer(
                    idGenerator.nextId(), row.assignmentId(), fromReviewerUserId,
                    toReviewerUserId, operatorUserId, TRANSFER_REASON, now);
            if (transferMapper.insert(transfer) != 1) {
                throw new IllegalStateException("任务审核人转交记录保存失败");
            }
        }
        return rows.size();
    }
}
