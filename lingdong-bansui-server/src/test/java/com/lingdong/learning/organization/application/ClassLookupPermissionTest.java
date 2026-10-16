package com.lingdong.learning.organization.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ClassLookupPermissionTest {
 @Test void assignmentLookupRequiresItsOwnActionAndOnlyReturnsActualScopedClasses(){
  var orgs=mock(OrganizationMapper.class);var scopes=mock(OrganizationDataScopeService.class);var features=mock(FeatureAccessService.class);var permissions=mock(PermissionDecisionService.class);
  var service=new ClassManagementApplicationService(orgs,null,scopes,null,features,null,null,permissions);
  var user=new AuthenticatedUser(1L,null,"u","User",AuthClientType.WEB,List.of());
  var own=Organization.create(11L,null,"ROOT","CLASS_A","Own","CLASS","/11/",0,null);
  when(scopes.findAccessibleOrganizations(1L)).thenReturn(List.of(own));when(orgs.findAll()).thenReturn(List.of(own));
  when(permissions.isAllowed(1L,PermissionClient.WEB,"TEACHER_CLASS_ASSIGN")).thenReturn(true);
  assertThat(service.lookup(user,"TEACHER_CLASS_ASSIGN","CLASS",null,null,null)).containsExactly(own);
  verify(permissions,never()).isAllowed(1L,PermissionClient.WEB,"CLASS_READ");
  when(permissions.isAllowed(1L,PermissionClient.WEB,"TEACHER_CLASS_ASSIGN")).thenReturn(false);
  when(permissions.isAllowed(1L,PermissionClient.WEB,"CLASS_READ")).thenReturn(true);
  assertThatThrownBy(()->service.lookup(user,"TEACHER_CLASS_ASSIGN","CLASS",null,null,null)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
 }
 @Test void userCreateLookupDoesNotRequireIndependentClassModuleFlag(){
  var orgs=mock(OrganizationMapper.class);var scopes=mock(OrganizationDataScopeService.class);var features=mock(FeatureAccessService.class);var permissions=mock(PermissionDecisionService.class);
  var service=new ClassManagementApplicationService(orgs,null,scopes,null,features,null,null,permissions);var user=new AuthenticatedUser(1L,null,"u","User",AuthClientType.WEB,List.of());
  when(scopes.findAccessibleOrganizations(1L)).thenReturn(List.of());when(orgs.findAll()).thenReturn(List.of());when(permissions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_CREATE")).thenReturn(true);
  doThrow(new IllegalStateException("班级管理关闭")).when(features).requireEnabled("CLASS_MANAGEMENT",null);
  assertThat(service.lookup(user,"IAM_USER_CREATE","CLASS",null,null,null)).isEmpty();verifyNoInteractions(features);
  assertThatThrownBy(()->service.listClasses(user)).hasMessageContaining("班级管理关闭");
 }
}
