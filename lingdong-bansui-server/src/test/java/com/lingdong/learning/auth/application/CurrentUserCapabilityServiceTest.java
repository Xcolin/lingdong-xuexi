package com.lingdong.learning.auth.application;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.learningtask.application.LearningTaskScopeService;
import com.lingdong.learning.teacher.application.TeacherIdentityService;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserCapabilityServiceTest {
    final UserMapper users=mock(UserMapper.class);
    final ParentAuthenticationMapper parents=mock(ParentAuthenticationMapper.class);
    final TeacherIdentityService teachers=mock(TeacherIdentityService.class);
    final LearningTaskScopeService tasks=mock(LearningTaskScopeService.class);
    final CurrentUserCapabilityService service=new CurrentUserCapabilityService(users,parents,teachers,tasks);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"custom","custom",AuthClientType.WEB,List.of("CUSTOM_FAMILY"));
    @Test void reportsPersistedFamilyProfileAndUsesSameBackendSourceEligibility() {
        when(users.findById(1L)).thenReturn(User.create(1L,"custom","custom",null,UserType.FAMILY));
        when(parents.findProfileByUserId(1L)).thenReturn(mock(ParentProfile.class));
        when(tasks.allowedSourceTypes(actor)).thenReturn(List.of("FAMILY"));
        assertThat(service.describe(actor)).isEqualTo(new CurrentUserCapabilityService.Capabilities(UserType.FAMILY,true,false,List.of("FAMILY")));
    }
    @Test void parentRoleNameCannotMakePlatformAccountAParent() {
        var spoof=new AuthenticatedUser(1L,2L,"platform","platform",AuthClientType.WEB,List.of("PARENT"));
        when(users.findById(1L)).thenReturn(User.create(1L,"platform","platform",null,UserType.PLATFORM));
        when(tasks.allowedSourceTypes(spoof)).thenReturn(List.of());
        assertThat(service.describe(spoof).parentProfile()).isFalse();
        verify(parents).findProfileByUserId(1L);
    }
    @Test void organizationAccountRetainsOriginalTypeWithRealFamilyProfile() {
        when(users.findById(1L)).thenReturn(User.create(1L,"org","org",null,UserType.ORGANIZATION));
        when(parents.findProfileByUserId(1L)).thenReturn(new ParentProfile(4L,1L,ParentOnboardingStatus.PENDING,null,null));
        when(tasks.allowedSourceTypes(actor)).thenReturn(List.of("FAMILY"));
        var result=service.describe(actor);assertThat(result.userType()).isEqualTo(UserType.ORGANIZATION);assertThat(result.parentProfile()).isTrue();assertThat(result.teacherProfile()).isFalse();
    }
    @Test void missingOrDisabledAccountIsRejected() {
        assertThatThrownBy(() -> service.describe(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(users.findById(1L)).thenReturn(new User(1L,"custom","custom",null,null,UserType.FAMILY,UserStatus.DISABLED,null,null));
        assertThatThrownBy(() -> service.describe(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
