package com.lingdong.learning.organization.application;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.auth.application.ParentProfile;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class FamilyOrganizationIdentityReadTest {
 @Test void realParentProfileReadsExactOwnFamilyWithoutParentRoleOrWriteScope(){
  var roles=mock(UserRoleMapper.class);var users=mock(UserMapper.class);var profiles=mock(ParentAuthenticationMapper.class);var memberships=mock(UserOrganizationMapper.class);var scopes=mock(OrganizationDataScopeService.class);var permissions=mock(PermissionDecisionService.class);var query=mock(OrganizationQueryApplicationService.class);
  var service=new OrganizationManagementApplicationService(roles,null,query,mock(FeatureAccessService.class),permissions,scopes);
  ReflectionTestUtils.setField(service,"users",users);ReflectionTestUtils.setField(service,"parentProfiles",profiles);ReflectionTestUtils.setField(service,"memberships",memberships);
  when(users.findById(1L)).thenReturn(User.create(1L,"parent","Parent",null,UserType.FAMILY));when(profiles.findProfileByUserId(1L)).thenReturn(mock(ParentProfile.class));when(permissions.isAllowed(1L,PermissionClient.WEB,"ORG_NODE_READ")).thenReturn(true);
  when(scopes.findAccessibleOrganizations(1L)).thenReturn(List.of());when(memberships.findOrganizationIds(1L)).thenReturn(List.of(10L));
  var family=Organization.create(10L,null,"ROOT","FAM","Own family","FAMILY","/FAM/",0,null);when(query.listOrganizationsByIds(List.of(10L))).thenReturn(List.of(family));
  assertThat(service.listOrganizations(1L)).containsExactly(family);verifyNoInteractions(roles);verify(scopes,never()).canAccess(1L,10L);
  when(profiles.findProfileByUserId(1L)).thenReturn(null);assertThat(service.listOrganizations(1L)).isEmpty();
 }
}
