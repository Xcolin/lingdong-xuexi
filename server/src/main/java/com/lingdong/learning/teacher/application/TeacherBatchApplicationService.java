package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 校验 Web 批量请求并逐项汇总独立事务结果。 */
@Service
public class TeacherBatchApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TeacherBatchApplicationService.class);
    private static final int MAX_BATCH_SIZE = 100;
    private static final String FAILURE_CODE = "TEACHER_OPERATION_FAILED";
    private static final String FAILURE_MESSAGE = "教师操作失败，请检查状态或数据范围";

    private final TeacherBatchItemService itemService;
    private final PermissionDecisionService permissionDecisionService;

    public TeacherBatchApplicationService(
            TeacherBatchItemService itemService,
            PermissionDecisionService permissionDecisionService
    ) {
        this.itemService = itemService;
        this.permissionDecisionService = permissionDecisionService;
    }

    public TeacherBatchResult execute(AuthenticatedUser currentUser, TeacherBatchCommand command) {
        Objects.requireNonNull(currentUser, "当前登录用户不能为空");
        Objects.requireNonNull(command, "教师批量操作请求不能为空");
        if (currentUser.clientType() != AuthClientType.WEB) {
            throw new IllegalArgumentException("教师批量操作仅支持 Web 端");
        }
        TeacherBatchOperation operation = Objects.requireNonNull(command.operation(), "批量操作类型不能为空");
        validateTeacherIds(command.teacherUserIds());
        if (operation.requiresClass()
                && (command.classOrganizationId() == null || command.classOrganizationId() <= 0)) {
            throw new IllegalArgumentException("班级绑定操作必须提供班级标识");
        }
        requireUnderlyingPermission(currentUser, operation);

        List<TeacherBatchItemResult> items = new ArrayList<>(command.teacherUserIds().size());
        int successCount = 0;
        for (Long teacherUserId : command.teacherUserIds()) {
            try {
                itemService.execute(currentUser, operation, teacherUserId, command.classOrganizationId());
                items.add(TeacherBatchItemResult.success(teacherUserId));
                successCount++;
            } catch (RuntimeException exception) {
                LOGGER.warn("教师批量操作单项失败，operation={}, teacherUserId={}, exceptionType={}",
                        operation, teacherUserId, exception.getClass().getSimpleName());
                items.add(TeacherBatchItemResult.failure(
                        teacherUserId, FAILURE_CODE, FAILURE_MESSAGE));
            }
        }
        return new TeacherBatchResult(
                successCount, command.teacherUserIds().size() - successCount, items);
    }

    private void requireUnderlyingPermission(
            AuthenticatedUser currentUser,
            TeacherBatchOperation operation
    ) {
        String permissionCode = operation.requiresClass()
                ? "TEACHER_CLASS_ASSIGN" : "TEACHER_STATUS_CHANGE";
        if (!permissionDecisionService.isAllowed(
                currentUser.userId(), PermissionClient.WEB, permissionCode)) {
            throw new SystemOperationAccessDeniedException("无权执行对应的教师批量操作");
        }
    }

    private void validateTeacherIds(List<Long> teacherUserIds) {
        if (teacherUserIds == null || teacherUserIds.isEmpty()) {
            throw new IllegalArgumentException("批量教师不能为空");
        }
        if (teacherUserIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("单次批量操作不能超过 100 位教师");
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long teacherUserId : teacherUserIds) {
            if (teacherUserId == null || teacherUserId <= 0) {
                throw new IllegalArgumentException("教师标识不合法");
            }
            if (!uniqueIds.add(teacherUserId)) {
                throw new IllegalArgumentException("批量教师不能重复");
            }
        }
    }
}
