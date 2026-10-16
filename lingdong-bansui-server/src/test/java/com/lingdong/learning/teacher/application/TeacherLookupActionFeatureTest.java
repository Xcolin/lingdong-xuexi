package com.lingdong.learning.teacher.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TeacherLookupActionFeatureTest {
 @Test void taskLookupUsesTaskActionRatherThanTeacherModuleFlag(){var features=mock(FeatureAccessService.class);var permissions=mock(PermissionDecisionService.class);var service=new TeacherManagementAccessService(features,null,null,permissions);var user=new AuthenticatedUser(1L,null,"u","User",AuthClientType.WEB,List.of());when(permissions.isAllowed(1L,PermissionClient.WEB,"LEARNING_TASK_CREATE")).thenReturn(true);doThrow(new IllegalStateException("教师管理关闭")).when(features).requireEnabled("TEACHER_MANAGEMENT",null);service.requireLookup(user,"LEARNING_TASK_CREATE");verifyNoInteractions(features);assertThatThrownBy(()->service.requireManager(user)).hasMessageContaining("教师管理关闭");when(permissions.isAllowed(1L,PermissionClient.WEB,"LEARNING_TASK_CREATE")).thenReturn(false);assertThatThrownBy(()->service.requireLookup(user,"LEARNING_TASK_CREATE")).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);}
}
