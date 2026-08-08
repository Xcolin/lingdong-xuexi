package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskOverdueStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskPauseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 将已到期且未暂停的进行中任务转换为中性待优化状态。 */
@Service
public class TaskOverdueService {
    private static final String REASON = "当日未提交打卡，状态转为待优化";

    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskPauseMapper pauseMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final IdGenerator idGenerator;

    public TaskOverdueService(
            LearningTaskAssignmentMapper assignmentMapper,
            TaskPauseMapper pauseMapper,
            TaskAssignmentEventMapper eventMapper,
            IdGenerator idGenerator
    ) {
        this.assignmentMapper = assignmentMapper;
        this.pauseMapper = pauseMapper;
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public boolean markNeedsImprovement(Long assignmentId, LocalDateTime cutoff) {
        TaskOverdueStateRow state = assignmentMapper.findOverdueStateForUpdate(assignmentId, cutoff);
        if (state == null || pauseMapper.findActive(state.id(), cutoff) != null) {
            return false;
        }
        pauseMapper.closeExpired(state.id(), cutoff);
        int updated = assignmentMapper.transitionStatus(
                state.id(), TaskAssignmentStatus.IN_PROGRESS.name(),
                TaskAssignmentStatus.NEEDS_IMPROVEMENT.name(), state.versionNo(), cutoff, null);
        if (updated != 1) {
            return false;
        }
        int inserted = eventMapper.insert(new TaskAssignmentEvent(
                idGenerator.nextId(), state.id(), TaskAssignmentEventType.MARKED_NEEDS_IMPROVEMENT,
                null, TaskAssignmentStatus.IN_PROGRESS, TaskAssignmentStatus.NEEDS_IMPROVEMENT,
                REASON, null, cutoff));
        if (inserted != 1) {
            throw new IllegalStateException("任务待优化事件写入失败");
        }
        return true;
    }
}
