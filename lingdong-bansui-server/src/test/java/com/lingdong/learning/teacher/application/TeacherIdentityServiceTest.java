package com.lingdong.learning.teacher.application;
import com.lingdong.learning.teacher.infrastructure.persistence.TeacherProfileMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class TeacherIdentityServiceTest {
 @Test void occupationalProfileRecognizesTeacherWithoutAnyRoleGrant(){var users=mock(UserMapper.class);var profiles=mock(TeacherProfileMapper.class);var service=new TeacherIdentityService(profiles,users,mock(com.lingdong.learning.feature.application.FeatureAccessService.class));when(users.findById(1L)).thenReturn(User.create(1L,"teacher","Teacher",null,UserType.ORGANIZATION));when(profiles.enabled(1L)).thenReturn(true);assertThat(service.isTeacher(1L)).isTrue();when(profiles.enabled(1L)).thenReturn(false);assertThat(service.isTeacher(1L)).isFalse();}
 @Test void profileCannotTurnOtherAccountTypesIntoTeacher(){var users=mock(UserMapper.class);var profiles=mock(TeacherProfileMapper.class);var service=new TeacherIdentityService(profiles,users,mock(com.lingdong.learning.feature.application.FeatureAccessService.class));when(users.findById(1L)).thenReturn(User.create(1L,"parent","Parent",null,UserType.FAMILY));assertThat(service.isTeacher(1L)).isFalse();assertThatThrownBy(()->service.register(1L)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(profiles);}
 @Test void trustedCreationDoesNotEnableOrRequireManagementWhileIndependentRegisterStillDoes(){
  var users=mock(UserMapper.class);var profiles=mock(TeacherProfileMapper.class);var features=mock(com.lingdong.learning.feature.application.FeatureAccessService.class);var permissions=mock(com.lingdong.learning.permission.application.PermissionDecisionService.class);
  var service=new TeacherIdentityService(profiles,users,features);org.springframework.test.util.ReflectionTestUtils.setField(service,"permissions",permissions);
  when(users.findById(2L)).thenReturn(User.create(2L,"teacher","Teacher",null,UserType.ORGANIZATION));when(permissions.isAllowed(1L,com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_CREATE")).thenReturn(true);
  doThrow(new IllegalStateException("教师管理关闭")).when(features).requireEnabled("TEACHER_MANAGEMENT",null);
  service.registerCreatedIdentity(1L,2L);verify(profiles).insert(2L);verifyNoInteractions(features);
  assertThatThrownBy(()->service.register(2L)).hasMessageContaining("教师管理关闭");
 }
}
