package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.ClassTaskAssignmentStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.ClassTaskInvalidationMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskPauseMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 班级停用时终止班内尚未完成的机构和教师任务。 */
@Service
public class ClassTaskInvalidationService {
    private static final String REASON = "班级停用，机构或教师任务自动失效";

    private final ClassTaskInvalidationMapper invalidationMapper;
    private final TaskPauseMapper pauseMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final IdGenerator idGenerator;

    public ClassTaskInvalidationService(
            ClassTaskInvalidationMapper invalidationMapper,
            TaskPauseMapper pauseMapper,
            TaskAssignmentEventMapper eventMapper,
            IdGenerator idGenerator
    ) {
        this.invalidationMapper = invalidationMapper;
        this.pauseMapper = pauseMapper;
        this.eventMapper = eventMapper;
        this.idGenerator = idGenerator;
    }

    /** 调用方必须处于组织停用事务中，确保班级和任务状态原子提交。 */
    public int invalidateUnfinishedAssignments(Long classOrganizationId, Long operatorUserId) {
        List<ClassTaskAssignmentStateRow> states = invalidationMapper
                .findUnfinishedByClassForUpdate(classOrganizationId);
        LocalDateTime now = LocalDateTime.now();
        for (ClassTaskAssignmentStateRow state : states) {
            if (invalidationMapper.invalidate(
                    state.assignmentId(), state.currentStatus(), state.versionNo(), now) != 1) {
                throw new IllegalStateException("班级任务状态已变化，请重新停用班级");
            }
            pauseMapper.terminateActive(state.assignmentId(), now);
            if (eventMapper.insert(new TaskAssignmentEvent(
                    idGenerator.nextId(), state.assignmentId(), TaskAssignmentEventType.CLASS_INVALIDATED,
                    operatorUserId, state.currentStatus(), TaskAssignmentStatus.INVALIDATED,
                    REASON, "classOrganizationId=" + classOrganizationId, now)) != 1) {
                throw new IllegalStateException("班级任务失效审计写入失败");
            }
        }
        return states.size();
    }
}
