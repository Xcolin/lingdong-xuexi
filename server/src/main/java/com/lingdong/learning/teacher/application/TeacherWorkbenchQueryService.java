package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 从当前会话解析教师权限和活动班级，不接受前端传入的数据范围。 */
@Service
public class TeacherWorkbenchQueryService {
    private static final String AUTH_FEATURE = "ORGANIZATION_MINIAPP_AUTH";
    private static final String TASK_FEATURE = "LEARNING_TASK_MANAGEMENT";

    private final UserMapper userMapper;
    private final TeacherClassMapper teacherClassMapper;
    private final PermissionDecisionService permissionDecisionService;
    private final FeatureAccessService featureAccessService;

    public TeacherWorkbenchQueryService(
            UserMapper userMapper,
            TeacherClassMapper teacherClassMapper,
            PermissionDecisionService permissionDecisionService,
            FeatureAccessService featureAccessService
    ) {
        this.userMapper = userMapper;
        this.teacherClassMapper = teacherClassMapper;
        this.permissionDecisionService = permissionDecisionService;
        this.featureAccessService = featureAccessService;
    }

    public TeacherWorkbenchContext getContext(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(AUTH_FEATURE, null);
        featureAccessService.requireEnabled(TASK_FEATURE, null);
        if (currentUser == null || currentUser.clientType() != AuthClientType.MINIAPP
                || !currentUser.roleCodes().contains("TEACHER")) {
            throw accessDenied();
        }
        User user = userMapper.findById(currentUser.userId());
        if (user == null || user.status() != UserStatus.ENABLED
                || user.type() != UserType.ORGANIZATION) {
            throw accessDenied();
        }
        List<TeacherClassSummary> classes = teacherClassMapper.findActiveClassSummaries(user.id());
        if (classes.isEmpty()) {
            throw accessDenied();
        }
        return new TeacherWorkbenchContext(
                user.id(), user.username(), user.displayName(),
                permissionDecisionService.findAllowedCodes(user.id(), PermissionClient.MINIAPP),
                classes);
    }

    private SystemOperationAccessDeniedException accessDenied() {
        return new SystemOperationAccessDeniedException("当前身份不能访问教师工作台");
    }
}
