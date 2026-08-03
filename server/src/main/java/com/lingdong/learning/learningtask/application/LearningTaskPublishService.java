package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskAssignment;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTargetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 以单事务完成任务复核、目标展开、实例落库和发布状态切换。 */
@Service
public class LearningTaskPublishService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final String INITIAL_STATUS = "PENDING_CLAIM";

    private final LearningTaskMapper taskMapper;
    private final LearningTaskTargetMapper targetMapper;
    private final LearningTaskTagMapper tagMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final LearningTaskValidator validator;
    private final LearningTaskScopeService scopeService;
    private final LearningTaskTargetExpansionService expansionService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;

    public LearningTaskPublishService(
            LearningTaskMapper taskMapper,
            LearningTaskTargetMapper targetMapper,
            LearningTaskTagMapper tagMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            LearningTaskValidator validator,
            LearningTaskScopeService scopeService,
            LearningTaskTargetExpansionService expansionService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator
    ) {
        this.taskMapper = taskMapper;
        this.targetMapper = targetMapper;
        this.tagMapper = tagMapper;
        this.assignmentMapper = assignmentMapper;
        this.validator = validator;
        this.scopeService = scopeService;
        this.expansionService = expansionService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public PublishLearningTaskResult publish(AuthenticatedUser currentUser, Long taskId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LearningTask task = taskMapper.findByIdForUpdate(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("任务不存在或不可访问");
        }
        scopeService.requireManageable(currentUser, task);
        if (task.status() != LearningTaskStatus.DRAFT) {
            throw new IllegalStateException("任务已发布，不能重复发布");
        }

        List<LearningTaskTarget> targets = targetMapper.findByTaskId(task.id());
        List<String> tagCodes = tagMapper.findCodesByTaskId(task.id());
        ValidatedLearningTaskDraft validatedDraft = validator.validate(new LearningTaskDraftInput(
                task.title(), task.difficultyLevel(), task.durationMinutes(), task.scheduledDate(),
                task.categoryCode(), tagCodes, task.remark(), targets.stream()
                .map(target -> new LearningTaskTargetInput(target.targetType(), target.targetId()))
                .toList()));
        Long reviewerUserId = scopeService.validateAndResolveReviewer(
                currentUser, task.sourceType(), task.sourceOrganizationId(),
                task.reviewerUserId(), validatedDraft);
        if (!reviewerUserId.equals(task.reviewerUserId())) {
            throw new IllegalStateException("任务审核人已变化，请重新保存草稿");
        }

        List<Long> studentIds = expansionService.expand(targets);
        if (studentIds.isEmpty()) {
            throw new IllegalStateException("任务目标下没有可发布的启用学生");
        }
        List<LearningTaskAssignment> assignments = new ArrayList<>(studentIds.size());
        for (Long studentId : studentIds) {
            assignments.add(new LearningTaskAssignment(
                    idGenerator.nextId(), task.id(), studentId, task.sourceType(),
                    task.sourceOrganizationId(), INITIAL_STATUS, task.reviewerUserId(),
                    task.scheduledDate(), task.scheduledDate().atTime(LocalTime.of(23, 59, 59))));
        }
        assignmentMapper.insertBatch(assignments);
        if (taskMapper.markPublished(task.id()) != 1) {
            throw new IllegalStateException("任务状态已变化，发布失败");
        }
        return new PublishLearningTaskResult(
                task.id(), assignments.size(), LearningTaskStatus.PUBLISHED);
    }
}
