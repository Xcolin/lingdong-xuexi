package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningTaskScopeServiceSecondaryParentTest {
    private final LearningTaskAssignmentMapper assignmentMapper = mock(LearningTaskAssignmentMapper.class);
    private final LearningTaskScopeService service = new LearningTaskScopeService(
            mock(OrganizationMapper.class), mock(OrganizationDataScopeService.class),
            mock(ParentStudentMapper.class), mock(StudentOrganizationMapper.class),
            mock(TeacherClassMapper.class), mock(UserMapper.class), mock(UserRoleMapper.class),
            assignmentMapper);

    @Test
    void linkedSecondaryParentReadsPublishedAssignmentButCannotManageTask() {
        long parentId = 8910000000000000831L;
        long taskId = 8910000000000000832L;
        AuthenticatedUser secondaryParent = new AuthenticatedUser(
                parentId, 1L, "secondary", "副家长", AuthClientType.WEB, List.of("PARENT"));
        LearningTask task = mock(LearningTask.class);
        when(task.id()).thenReturn(taskId);
        when(task.status()).thenReturn(LearningTaskStatus.PUBLISHED);
        when(task.sourceType()).thenReturn(LearningTaskSourceType.FAMILY);
        when(task.creatorUserId()).thenReturn(8910000000000000833L);
        when(assignmentMapper.existsTaskAssignedToActiveParent(taskId, parentId)).thenReturn(true);

        assertThatCode(() -> service.requireReadable(secondaryParent, task)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireManageable(secondaryParent, task))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unrelatedParentCannotReadPublishedTask() {
        long parentId = 8910000000000000841L;
        long taskId = 8910000000000000842L;
        AuthenticatedUser unrelatedParent = new AuthenticatedUser(
                parentId, 1L, "other", "无关家长", AuthClientType.WEB, List.of("PARENT"));
        LearningTask task = mock(LearningTask.class);
        when(task.id()).thenReturn(taskId);
        when(task.status()).thenReturn(LearningTaskStatus.PUBLISHED);
        when(task.sourceType()).thenReturn(LearningTaskSourceType.FAMILY);
        when(task.creatorUserId()).thenReturn(8910000000000000843L);

        assertThatThrownBy(() -> service.requireReadable(unrelatedParent, task))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void teacherReadsPublishedOrganizationTaskOnlyWhenAnActiveClassStudentReceivedIt() {
        long teacherId = 8910000000000000851L;
        long taskId = 8910000000000000852L;
        AuthenticatedUser teacher = new AuthenticatedUser(
                teacherId, 1L, "teacher", "教师", AuthClientType.MINIAPP, List.of("TEACHER"));
        LearningTask task = mock(LearningTask.class);
        when(task.id()).thenReturn(taskId);
        when(task.status()).thenReturn(LearningTaskStatus.PUBLISHED);
        when(task.sourceType()).thenReturn(LearningTaskSourceType.ORGANIZATION);
        when(task.creatorUserId()).thenReturn(8910000000000000853L);
        when(assignmentMapper.existsTaskVisibleToActiveTeacher(taskId, teacherId)).thenReturn(true);

        assertThatCode(() -> service.requireProgressReadable(teacher, task)).doesNotThrowAnyException();

        when(assignmentMapper.existsTaskVisibleToActiveTeacher(taskId, teacherId)).thenReturn(false);
        assertThatThrownBy(() -> service.requireProgressReadable(teacher, task))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void parentCannotReadManagedProgressEvenForAnAssignedPublishedTask() {
        AuthenticatedUser parent = new AuthenticatedUser(
                8910000000000000861L, 1L, "parent", "家长",
                AuthClientType.WEB, List.of("PARENT"));
        LearningTask task = mock(LearningTask.class);
        when(task.sourceType()).thenReturn(LearningTaskSourceType.ORGANIZATION);

        assertThatThrownBy(() -> service.requireProgressReadable(parent, task))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
