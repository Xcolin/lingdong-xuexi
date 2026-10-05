package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ManagedTaskProgressMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按服务端数据范围分页查询机构或教师任务的学生级进度。 */
@Service
public class ManagedTaskProgressService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";

    private final LearningTaskMapper taskMapper;
    private final ManagedTaskProgressMapper progressMapper;
    private final LearningTaskScopeService scopeService;
    private final FeatureAccessService featureAccessService;

    public ManagedTaskProgressService(
            LearningTaskMapper taskMapper,
            ManagedTaskProgressMapper progressMapper,
            LearningTaskScopeService scopeService,
            FeatureAccessService featureAccessService
    ) {
        this.taskMapper = taskMapper;
        this.progressMapper = progressMapper;
        this.scopeService = scopeService;
        this.featureAccessService = featureAccessService;
    }

    @Transactional(readOnly = true)
    public ManagedTaskProgressPage findPage(
            AuthenticatedUser currentUser, Long taskId, int page, int pageSize
    ) {
        scopeService.requireWebPermission(currentUser, "LEARNING_TASK_PROGRESS_READ");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("任务标识不合法");
        }
        int normalizedPage = requireRange(page, "页码", 1, Integer.MAX_VALUE);
        int normalizedPageSize = requireRange(pageSize, "每页数量", 1, 100);
        LearningTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("任务不存在或不可访问");
        }
        scopeService.requireProgressReadable(currentUser, task);
        Long teacherUserId = currentUser.roleCodes().contains("TEACHER")
                && !scopeService.canReadOrganizationProgress(currentUser, task) ? currentUser.userId() : null;
        ManagedTaskProgressQuery query = new ManagedTaskProgressQuery(
                task.id(), teacherUserId, normalizedPageSize,
                Math.multiplyExact(normalizedPage - 1, normalizedPageSize));
        return new ManagedTaskProgressPage(
                progressMapper.findPage(query).stream().map(this::toView).toList(),
                normalizedPage, normalizedPageSize, progressMapper.count(query));
    }

    private ManagedTaskProgressView toView(ManagedTaskProgressRow row) {
        return new ManagedTaskProgressView(
                row.assignmentId(), row.studentId(), row.studentName(),
                maskStudentAccount(row.studentAccount()), row.classOrganizationId(), row.className(),
                row.currentStatus(), row.scheduledDate(), row.claimedAt(), row.completedAt(),
                row.lastTransitionAt());
    }

    private String maskStudentAccount(String account) {
        if (account == null || account.length() < 4) {
            return account == null ? null : "****";
        }
        return account.substring(0, 2) + "****" + account.substring(account.length() - 2);
    }

    private int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return value;
    }
}
