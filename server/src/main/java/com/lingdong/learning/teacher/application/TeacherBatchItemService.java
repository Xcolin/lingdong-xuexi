package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.learningtask.application.TeacherClassAssignmentService;
import com.lingdong.learning.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 为批量请求中的每位教师建立独立事务，确保单项失败互不影响。 */
@Service
public class TeacherBatchItemService {
    private final TeacherManagementApplicationService teacherManagementService;
    private final TeacherClassAssignmentService teacherClassAssignmentService;

    public TeacherBatchItemService(
            TeacherManagementApplicationService teacherManagementService,
            TeacherClassAssignmentService teacherClassAssignmentService
    ) {
        this.teacherManagementService = teacherManagementService;
        this.teacherClassAssignmentService = teacherClassAssignmentService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(
            AuthenticatedUser currentUser,
            TeacherBatchOperation operation,
            Long teacherUserId,
            Long classOrganizationId
    ) {
        Objects.requireNonNull(operation, "批量操作类型不能为空");
        switch (operation) {
            case ENABLE -> teacherManagementService.changeStatus(
                    currentUser, teacherUserId, UserStatus.ENABLED);
            case DISABLE -> teacherManagementService.changeStatus(
                    currentUser, teacherUserId, UserStatus.DISABLED);
            case LOCK -> teacherManagementService.changeStatus(
                    currentUser, teacherUserId, UserStatus.LOCKED);
            case BIND_CLASS -> teacherClassAssignmentService.assign(
                    currentUser, teacherUserId, classOrganizationId);
            case UNBIND_CLASS -> teacherClassAssignmentService.remove(
                    currentUser, teacherUserId, classOrganizationId);
        }
    }
}
