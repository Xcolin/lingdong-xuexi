package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ManagedTaskProgressMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedTaskProgressServiceTest {
    private final LearningTaskMapper taskMapper = mock(LearningTaskMapper.class);
    private final ManagedTaskProgressMapper progressMapper = mock(ManagedTaskProgressMapper.class);
    private final LearningTaskScopeService scopeService = mock(LearningTaskScopeService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final ManagedTaskProgressService service = new ManagedTaskProgressService(
            taskMapper, progressMapper, scopeService, featureAccessService);

    @Test
    void restrictsTeacherQueryAndMasksStudentAccount() {
        long taskId = 8910000000000000921L;
        AuthenticatedUser teacher = new AuthenticatedUser(
                8910000000000000922L, 1L, "teacher", "张老师",
                AuthClientType.MINIAPP, List.of("TEACHER"));
        LearningTask task = mock(LearningTask.class);
        when(task.id()).thenReturn(taskId);
        when(taskMapper.findById(taskId)).thenReturn(task);
        when(progressMapper.findPage(any())).thenReturn(List.of(new ManagedTaskProgressRow(
                8910000000000000923L, 8910000000000000924L, "李同学", "12345678",
                8910000000000000925L, "一年级一班", TaskAssignmentStatus.IN_PROGRESS,
                LocalDate.of(2026, 9, 6), null, null, null)));
        when(progressMapper.count(any())).thenReturn(1L);

        ManagedTaskProgressPage result = service.findPage(teacher, taskId, 1, 20);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().get(0).studentAccountMasked()).isEqualTo("12****78");
        verify(scopeService).requireProgressReadable(teacher, task);
        verify(progressMapper).findPage(new ManagedTaskProgressQuery(taskId, teacher.userId(), 20, 0));
    }

    @Test
    void organizationAdministratorUsesTaskScopeWithoutTeacherClassFilter() {
        long taskId = 8910000000000000931L;
        AuthenticatedUser administrator = new AuthenticatedUser(
                8910000000000000932L, 1L, "admin", "机构管理员",
                AuthClientType.WEB, List.of("ORG_ADMIN"));
        LearningTask task = mock(LearningTask.class);
        when(task.id()).thenReturn(taskId);
        when(taskMapper.findById(taskId)).thenReturn(task);
        when(progressMapper.findPage(any())).thenReturn(List.of());

        service.findPage(administrator, taskId, 2, 10);

        verify(progressMapper).findPage(new ManagedTaskProgressQuery(taskId, null, 10, 10));
    }
}
