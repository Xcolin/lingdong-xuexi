package com.lingdong.learning.teacher.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.teacher.application.TeacherAccount;
import com.lingdong.learning.teacher.application.TeacherBatchApplicationService;
import com.lingdong.learning.teacher.application.TeacherManagementApplicationService;
import com.lingdong.learning.user.domain.UserStatus;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeacherManagementControllerTest {
    private final TeacherManagementApplicationService managementService =
            mock(TeacherManagementApplicationService.class);
    private final TeacherBatchApplicationService batchService =
            mock(TeacherBatchApplicationService.class);
    private final TeacherManagementController controller =
            new TeacherManagementController(managementService, batchService);
    private final AuthenticatedUser currentUser = new AuthenticatedUser(
            101L, 201L, "org-admin", "机构管理员", AuthClientType.WEB, List.of("ORG_ADMIN"));

    @Test
    void masksTeacherMobileAndNeverExposesPasswordMaterial() {
        TeacherAccount account = new TeacherAccount(
                301L, "teacher-301", "张老师", "13800138000", UserStatus.ENABLED,
                401L, "第一学校", List.of(501L), LocalDateTime.now(), LocalDateTime.now());
        when(managementService.get(currentUser, 301L)).thenReturn(account);

        TeacherResponse response = controller.get(currentUser, 301L);

        assertThat(response.mobile()).isEqualTo("138****8000");
        assertThat(response.toString()).doesNotContain("password", "Password", "$2a$");
    }

    @Test
    void bindsEveryEndpointToItsMinimumPermission() throws Exception {
        assertPermission("list", "TEACHER_READ", AuthenticatedUser.class, String.class,
                Long.class, Long.class, UserStatus.class, int.class, int.class);
        assertPermission("get", "TEACHER_READ", AuthenticatedUser.class, Long.class);
        assertPermission("create", "TEACHER_CREATE", AuthenticatedUser.class, CreateTeacherRequest.class);
        assertPermission("updateProfile", "TEACHER_UPDATE", AuthenticatedUser.class,
                Long.class, UpdateTeacherProfileRequest.class);
        assertPermission("changeStatus", "TEACHER_STATUS_CHANGE", AuthenticatedUser.class,
                Long.class, ChangeTeacherStatusRequest.class);
        assertPermission("resetPassword", "TEACHER_PASSWORD_RESET", AuthenticatedUser.class,
                Long.class, ResetTeacherPasswordRequest.class);
        assertPermission("batch", "TEACHER_BATCH_MANAGE", AuthenticatedUser.class, TeacherBatchRequest.class);
    }

    private void assertPermission(String methodName, String code, Class<?>... parameterTypes) throws Exception {
        Method method = TeacherManagementController.class.getMethod(methodName, parameterTypes);
        RequirePermission permission = method.getAnnotation(RequirePermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(code);
    }
}
