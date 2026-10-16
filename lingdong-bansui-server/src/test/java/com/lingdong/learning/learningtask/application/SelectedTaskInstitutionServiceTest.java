package com.lingdong.learning.learningtask.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.learningtask.domain.*;
import com.lingdong.learning.learningtask.infrastructure.persistence.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SelectedTaskInstitutionServiceTest {
 private final Organization a=Organization.create(1L,null,"ROOT","A","A","SCHOOL","/A/",0,null);
 private final Organization b=Organization.create(2L,null,"ROOT","B","B","SCHOOL","/B/",0,null);
 private final AuthenticatedUser actor=new AuthenticatedUser(9L,1L,"admin","Admin",AuthClientType.MINIAPP,List.of("ORG_ADMIN"));
 private final OrganizationMapper organizations=mock(OrganizationMapper.class);
 private final OrganizationDataScopeService data=mock(OrganizationDataScopeService.class);
 private void setup(){when(organizations.findAll()).thenReturn(List.of(a,b));when(organizations.findById(1L)).thenReturn(a);when(organizations.findById(2L)).thenReturn(b);when(data.canAccess(9L,1L)).thenReturn(true);when(data.canAccess(9L,2L)).thenReturn(true);}
 private void selectA(){var request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,a);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));}
 @Test void optionsIntersectExistingTwoSchoolPermissionsWithSelectedSchool(){
  setup();var mapper=mock(LearningTaskOptionMapper.class);var scopes=mock(LearningTaskScopeService.class);when(scopes.isOrganizationOperator(actor)).thenReturn(true);
  when(mapper.findOrganizationOptionsForAdministrator(9L,null)).thenReturn(List.of(new OrganizationOption(1L,"A","SCHOOL",null,"/A/"),new OrganizationOption(2L,"B","SCHOOL",null,"/B/")));
  var service=new LearningTaskOptionService(mapper,mock(TeacherClassMapper.class),organizations,data,mock(com.lingdong.learning.feature.application.FeatureAccessService.class),scopes,mock(com.lingdong.learning.organization.application.OrganizationNamePathService.class));
  selectA();try{assertEquals(List.of(1L),service.organizations(actor,LearningTaskSourceType.ORGANIZATION,null).stream().map(OrganizationOption::id).toList());}finally{RequestContextHolder.resetRequestAttributes();}
  assertEquals(2,service.organizations(actor,LearningTaskSourceType.ORGANIZATION,null).size());
 }
 @Test void submissionRejectsOtherSchoolEvenWhenAdministratorCanAccessBoth(){
  setup();var users=mock(UserMapper.class);var account=mock(User.class);when(account.status()).thenReturn(UserStatus.ENABLED);when(account.type()).thenReturn(UserType.ORGANIZATION);when(users.findById(9L)).thenReturn(account);
  var service=new LearningTaskScopeService(organizations,data,mock(ParentStudentMapper.class),mock(StudentOrganizationMapper.class),mock(TeacherClassMapper.class),users,mock(UserRoleMapper.class),mock(LearningTaskAssignmentMapper.class),mock(com.lingdong.learning.permission.application.PermissionDecisionService.class),mock(ParentActorAccessService.class),mock(com.lingdong.learning.teacher.application.TeacherIdentityService.class));
  var draft=new ValidatedLearningTaskDraft("Task",1,10,30,java.time.LocalDate.now(),null,List.of(),null,List.of());
  selectA();try{assertEquals(Long.valueOf(9L),service.validateAndResolveReviewer(actor,LearningTaskSourceType.ORGANIZATION,1L,null,draft));assertThrows(ResourceNotFoundException.class,()->service.validateAndResolveReviewer(actor,LearningTaskSourceType.ORGANIZATION,2L,null,draft));}finally{RequestContextHolder.resetRequestAttributes();}
  assertEquals(Long.valueOf(9L),service.validateAndResolveReviewer(actor,LearningTaskSourceType.ORGANIZATION,1L,null,draft));assertEquals(Long.valueOf(9L),service.validateAndResolveReviewer(actor,LearningTaskSourceType.ORGANIZATION,2L,null,draft));
 }
}
