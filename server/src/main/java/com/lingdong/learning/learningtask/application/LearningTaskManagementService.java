package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskTag;
import com.lingdong.learning.learningtask.domain.LearningTaskTarget;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTargetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 管理任务草稿及其原始目标、标签聚合。 */
@Service
public class LearningTaskManagementService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";

    private final LearningTaskMapper taskMapper;
    private final LearningTaskTargetMapper targetMapper;
    private final LearningTaskTagMapper tagMapper;
    private final LearningTaskValidator validator;
    private final LearningTaskScopeService scopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;

    public LearningTaskManagementService(
            LearningTaskMapper taskMapper,
            LearningTaskTargetMapper targetMapper,
            LearningTaskTagMapper tagMapper,
            LearningTaskValidator validator,
            LearningTaskScopeService scopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator
    ) {
        this.taskMapper = taskMapper;
        this.targetMapper = targetMapper;
        this.tagMapper = tagMapper;
        this.validator = validator;
        this.scopeService = scopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public LearningTaskDetails create(
            AuthenticatedUser currentUser, CreateLearningTaskCommand command
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Objects.requireNonNull(command, "创建任务命令不能为空");
        ValidatedLearningTaskDraft draft = validator.validate(command.draft());
        Long reviewerUserId = scopeService.validateAndResolveReviewer(
                currentUser, command.sourceType(), command.sourceOrganizationId(),
                command.reviewerUserId(), draft);
        LearningTask task = LearningTask.draft(
                idGenerator.nextId(), command.sourceType(), command.sourceOrganizationId(),
                currentUser.userId(), reviewerUserId, draft);
        taskMapper.insert(task);
        persistTargetsAndTags(task.id(), draft);
        return requireDetails(task.id());
    }

    @Transactional
    public LearningTaskDetails update(
            AuthenticatedUser currentUser, Long taskId, CreateLearningTaskCommand command
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Objects.requireNonNull(command, "更新任务命令不能为空");
        LearningTask existing = taskMapper.findByIdForUpdate(taskId);
        if (existing == null) {
            throw notFound();
        }
        scopeService.requireManageable(currentUser, existing);
        requireDraft(existing);
        if (command.sourceType() != existing.sourceType()
                || !Objects.equals(command.sourceOrganizationId(), existing.sourceOrganizationId())) {
            throw new IllegalArgumentException("任务来源创建后不可修改");
        }

        ValidatedLearningTaskDraft draft = validator.validate(command.draft());
        Long reviewerUserId = scopeService.validateAndResolveReviewer(
                currentUser, existing.sourceType(), existing.sourceOrganizationId(),
                command.reviewerUserId(), draft);
        LearningTask updated = existing.withDraft(draft, reviewerUserId);
        if (taskMapper.updateDraft(updated) != 1) {
            throw new IllegalStateException("任务状态已变化，无法更新");
        }
        targetMapper.deleteByTaskId(taskId);
        tagMapper.deleteByTaskId(taskId);
        persistTargetsAndTags(taskId, draft);
        return requireDetails(taskId);
    }

    @Transactional(readOnly = true)
    public LearningTaskDetails findById(AuthenticatedUser currentUser, Long taskId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LearningTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw notFound();
        }
        scopeService.requireManageable(currentUser, task);
        return details(task);
    }

    @Transactional(readOnly = true)
    public LearningTaskPage findPage(
            AuthenticatedUser currentUser,
            LearningTaskSourceType sourceType,
            LearningTaskStatus status,
            LocalDate scheduledDate,
            String keyword,
            int page,
            int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireManagementRole(currentUser);
        int validatedPage = requireRange(page, "页码", 1, Integer.MAX_VALUE);
        int validatedPageSize = requireRange(pageSize, "每页数量", 1, 100);
        String normalizedKeyword = optionalText(keyword, 50);
        LearningTaskQuery query = new LearningTaskQuery(
                currentUser.userId(), currentUser.roleCodes().contains("PARENT"),
                currentUser.roleCodes().contains("ORG_ADMIN"),
                currentUser.roleCodes().contains("TEACHER"), sourceType, status,
                scheduledDate, normalizedKeyword,
                Math.multiplyExact(validatedPage - 1, validatedPageSize), validatedPageSize);
        return new LearningTaskPage(
                taskMapper.findPage(query), validatedPage, validatedPageSize, taskMapper.count(query));
    }

    LearningTaskDetails requireDetails(Long taskId) {
        LearningTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw notFound();
        }
        return details(task);
    }

    private LearningTaskDetails details(LearningTask task) {
        return new LearningTaskDetails(
                task, targetMapper.findByTaskId(task.id()), tagMapper.findCodesByTaskId(task.id()));
    }

    private void persistTargetsAndTags(Long taskId, ValidatedLearningTaskDraft draft) {
        for (LearningTaskTargetInput input : draft.targets()) {
            targetMapper.insert(new LearningTaskTarget(
                    idGenerator.nextId(), taskId, input.targetType(), input.targetId(), null));
        }
        for (String tagCode : draft.tagCodes()) {
            tagMapper.insert(new LearningTaskTag(
                    idGenerator.nextId(), taskId, tagCode, null));
        }
    }

    private void requireDraft(LearningTask task) {
        if (task.status() != LearningTaskStatus.DRAFT) {
            throw new IllegalStateException("已发布任务不可编辑");
        }
    }

    private void requireManagementRole(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.roleCodes().stream().noneMatch(
                role -> role.equals("PARENT") || role.equals("ORG_ADMIN") || role.equals("TEACHER"))) {
            throw new SystemOperationAccessDeniedException("当前角色不能管理学习任务");
        }
    }

    private int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return value;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("关键字长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("任务不存在或不可访问");
    }
}
