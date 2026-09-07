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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherWorkbenchQueryServiceTest {
    private final UserMapper userMapper = mock(UserMapper.class);
    private final TeacherClassMapper teacherClassMapper = mock(TeacherClassMapper.class);
    private final PermissionDecisionService permissionDecisionService = mock(PermissionDecisionService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final TeacherWorkbenchQueryService service = new TeacherWorkbenchQueryService(
            userMapper, teacherClassMapper, permissionDecisionService, featureAccessService);

    @Test
    void returnsOnlyCurrentTeachersActiveClassesAndMiniappPermissions() {
        AuthenticatedUser currentUser = new AuthenticatedUser(
                8910000000000000901L, 1L, "teacher", "张老师",
                AuthClientType.MINIAPP, List.of("TEACHER"));
        when(userMapper.findById(currentUser.userId())).thenReturn(new User(
                currentUser.userId(), "teacher", "张老师", null, null,
                UserType.ORGANIZATION, UserStatus.ENABLED, null, null));
        when(teacherClassMapper.findActiveClassSummaries(currentUser.userId())).thenReturn(List.of(
                new TeacherClassSummary(8910000000000000902L, "一年级一班", 8910000000000000903L, "示范学校")));
        when(permissionDecisionService.findAllowedCodes(
                currentUser.userId(), PermissionClient.MINIAPP)).thenReturn(List.of(
                "LEARNING_TASK_CREATE", "LEARNING_TASK_READ_MANAGED"));

        TeacherWorkbenchContext result = service.getContext(currentUser);

        assertThat(result.userId()).isEqualTo(currentUser.userId());
        assertThat(result.classes()).extracting(TeacherClassSummary::className)
                .containsExactly("一年级一班");
        assertThat(result.permissionCodes()).containsExactly(
                "LEARNING_TASK_CREATE", "LEARNING_TASK_READ_MANAGED");
        verify(featureAccessService).requireEnabled("ORGANIZATION_MINIAPP_AUTH", null);
        verify(featureAccessService).requireEnabled("LEARNING_TASK_MANAGEMENT", null);
    }

    @Test
    void rejectsWebAndNonTeacherSessions() {
        AuthenticatedUser webTeacher = new AuthenticatedUser(
                8910000000000000911L, 1L, "teacher", "张老师",
                AuthClientType.WEB, List.of("TEACHER"));
        AuthenticatedUser miniappAdministrator = new AuthenticatedUser(
                8910000000000000912L, 1L, "admin", "管理员",
                AuthClientType.MINIAPP, List.of("ORG_ADMIN"));

        assertThatThrownBy(() -> service.getContext(webTeacher))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> service.getContext(miniappAdministrator))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
