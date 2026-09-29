package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherBatchApplicationServiceTest {
    private final TeacherBatchItemService itemService = mock(TeacherBatchItemService.class);
    private final PermissionDecisionService permissionDecisionService = mock(PermissionDecisionService.class);
    private final TeacherBatchApplicationService service =
            new TeacherBatchApplicationService(itemService, permissionDecisionService);
    private final AuthenticatedUser currentUser = new AuthenticatedUser(
            101L, 201L, "org-admin", "机构管理员", AuthClientType.WEB, List.of("ORG_ADMIN"));

    @BeforeEach
    void allowUnderlyingPermissionsByDefault() {
        when(permissionDecisionService.isAllowed(
                eq(101L), eq(PermissionClient.WEB), anyString())).thenReturn(true);
    }

    @Test
    void rejectsMiniappDuplicateMissingClassAndOversizedRequests() {
        AuthenticatedUser miniappUser = new AuthenticatedUser(
                101L, 201L, "org-admin", "机构管理员", AuthClientType.MINIAPP, List.of("ORG_ADMIN"));
        assertThatThrownBy(() -> service.execute(miniappUser,
                new TeacherBatchCommand(TeacherBatchOperation.ENABLE, List.of(11L), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute(currentUser,
                new TeacherBatchCommand(TeacherBatchOperation.ENABLE, List.of(11L, 11L), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute(currentUser,
                new TeacherBatchCommand(TeacherBatchOperation.BIND_CLASS, List.of(11L), null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute(currentUser, new TeacherBatchCommand(
                TeacherBatchOperation.ENABLE,
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList(), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void continuesAfterItemFailureAndReturnsNeutralResult() {
        TeacherBatchCommand command = new TeacherBatchCommand(
                TeacherBatchOperation.BIND_CLASS, List.of(11L, 12L, 13L), 901L);
        doThrow(new ResourceNotFoundException("教师属于其他学校"))
                .when(itemService).execute(currentUser, TeacherBatchOperation.BIND_CLASS, 12L, 901L);

        TeacherBatchResult result = service.execute(currentUser, command);

        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.items()).extracting(TeacherBatchItemResult::teacherUserId)
                .containsExactly(11L, 12L, 13L);
        assertThat(result.items().get(1).errorCode()).isEqualTo("TEACHER_OPERATION_FAILED");
        assertThat(result.items().get(1).message())
                .isEqualTo("教师操作失败，请检查状态或数据范围")
                .doesNotContain("其他学校");
        verify(itemService).execute(currentUser, TeacherBatchOperation.BIND_CLASS, 13L, 901L);
    }

    @Test
    void rejectsBatchOperationWithoutItsUnderlyingPermission() {
        when(permissionDecisionService.isAllowed(
                101L, PermissionClient.WEB, "TEACHER_CLASS_ASSIGN")).thenReturn(false);

        assertThatThrownBy(() -> service.execute(currentUser, new TeacherBatchCommand(
                TeacherBatchOperation.BIND_CLASS, List.of(11L), 901L)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(itemService, never()).execute(
                currentUser, TeacherBatchOperation.BIND_CLASS, 11L, 901L);
    }

    @Test
    void itemWorkerUsesRequiresNewBoundary() throws Exception {
        Method method = TeacherBatchItemService.class.getMethod(
                "execute", AuthenticatedUser.class, TeacherBatchOperation.class, Long.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
