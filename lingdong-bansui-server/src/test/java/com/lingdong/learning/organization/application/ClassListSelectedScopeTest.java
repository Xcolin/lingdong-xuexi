package com.lingdong.learning.organization.application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.*;
import com.lingdong.learning.datascope.application.*;
class ClassListSelectedScopeTest {
 @AfterEach void clear(){org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
 @Test void selectedInstitutionClipsMiniappListsButLegacyUnselectedStillWorks(){
  var mapper=mock(OrganizationMapper.class);var scopes=mock(OrganizationDataScopeService.class);
  var permissions=mock(com.lingdong.learning.permission.application.PermissionDecisionService.class);
  when(permissions.isAllowed(anyLong(),any(),anyString())).thenReturn(true);
  var service=new ClassManagementApplicationService(mapper,mock(OrganizationChangeAuditMapper.class),scopes,mock(OrganizationApplicationService.class),mock(com.lingdong.learning.feature.application.FeatureAccessService.class),mock(com.lingdong.learning.learningtask.application.ClassTaskInvalidationService.class),mock(com.lingdong.learning.common.id.IdGenerator.class),permissions);
  var actor=new com.lingdong.learning.auth.application.AuthenticatedUser(4L,1L,"fixture","fixture",com.lingdong.learning.auth.domain.AuthClientType.MINIAPP,List.of("ORG_ADMIN"));
  var a=Organization.create(1L,null,"ROOT","a","a","SCHOOL","/1/",0,null);var b=Organization.create(2L,null,"ROOT","b","b","SCHOOL","/2/",0,null);
  var ca=Organization.create(11L,1L,"ORG:1","ca","ca","CLASS","/1/11/",0,null);var cb=Organization.create(21L,2L,"ORG:2","cb","cb","CLASS","/2/21/",0,null);
  when(mapper.findOperationalSchoolsByOrganizationAdministrator(4L)).thenReturn(List.of(a,b));when(mapper.findClassesByOrganizationAdministrator(4L)).thenReturn(List.of(ca,cb));
  assertThat(service.listManageableSchools(actor)).hasSize(2);assertThat(service.listClasses(actor)).hasSize(2);
  var request=new org.springframework.mock.web.MockHttpServletRequest();request.setAttribute(com.lingdong.learning.common.security.SelectedOrganizationContext.ATTRIBUTE,a);
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
  when(scopes.resolve(4L)).thenReturn(new OrganizationDataScope(false,false,List.of("/1/")));
  assertThat(service.listManageableSchools(actor)).containsExactly(a);assertThat(service.listClasses(actor)).containsExactly(ca);
  request.setAttribute(com.lingdong.learning.common.security.SelectedOrganizationContext.ATTRIBUTE,ca);
  when(scopes.resolve(4L)).thenReturn(new OrganizationDataScope(false,false,List.of("/1/11/")));
  assertThat(service.listManageableSchools(actor)).containsExactly(a);assertThat(service.listClasses(actor)).containsExactly(ca);
  when(scopes.resolve(4L)).thenReturn(OrganizationDataScope.empty());
  assertThat(service.listManageableSchools(actor)).isEmpty();assertThat(service.listClasses(actor)).isEmpty();
 }
}

