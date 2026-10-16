package com.lingdong.learning.learningtask.application;
import com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper;
import com.lingdong.learning.learningtask.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.teacher.application.TeacherIdentityService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class TeacherCreatedIdentityPlacementTest {
 @Test void trustedInitialClassBindingIgnoresManagementFlagsButDirectBindingDoesNot(){
  var relations=mock(TeacherClassMapper.class);var users=mock(UserMapper.class);var roles=mock(UserRoleMapper.class);var orgs=mock(OrganizationMapper.class);var scopes=mock(OrganizationDataScopeService.class);var features=mock(FeatureAccessService.class);var ids=mock(IdGenerator.class);var memberships=mock(UserOrganizationMapper.class);var identity=mock(TeacherIdentityService.class);var permissions=mock(PermissionDecisionService.class);
  var service=new TeacherClassAssignmentService(relations,users,roles,orgs,scopes,features,ids,null,null);ReflectionTestUtils.setField(service,"userOrganizationMapper",memberships);ReflectionTestUtils.setField(service,"teacherIdentity",identity);ReflectionTestUtils.setField(service,"actionPermissions",permissions);
  var user=new AuthenticatedUser(1L,null,"u","User",AuthClientType.WEB,List.of());var cls=Organization.create(10L,null,"ROOT","CLASS","Class","CLASS","/CLASS/",0,null);
  when(orgs.findById(10L)).thenReturn(cls);when(scopes.canAccess(1L,10L)).thenReturn(true);when(scopes.resolve(1L)).thenReturn(OrganizationDataScope.all(false));when(memberships.findOrganizationIds(2L)).thenReturn(List.of(10L));when(users.findByIdForUpdate(2L)).thenReturn(User.create(2L,"teacher","Teacher",null,UserType.ORGANIZATION));when(identity.isTeacher(2L)).thenReturn(true);when(relations.existsTeacherOrganizationInClassAncestors(2L,10L)).thenReturn(true);when(ids.nextId()).thenReturn(99L);when(relations.insert(any())).thenReturn(1);when(relations.insertChangeLog(anyLong(),anyLong(),anyLong(),any(),anyLong())).thenReturn(1);when(permissions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_CREATE")).thenReturn(true);
  doThrow(new IllegalStateException("教师管理关闭")).when(features).requireEnabled("TEACHER_MANAGEMENT",null);
  service.assignCreatedTeacher(user,2L,10L);verify(relations).insert(any());verifyNoInteractions(features);
  assertThatThrownBy(()->service.assign(user,2L,10L)).hasMessageContaining("教师管理关闭");
 }
}
