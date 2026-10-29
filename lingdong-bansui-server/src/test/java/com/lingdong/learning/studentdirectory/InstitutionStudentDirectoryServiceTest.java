package com.lingdong.learning.studentdirectory;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.application.StudentManagementAccessService;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InstitutionStudentDirectoryServiceTest {
 final InstitutionStudentDirectoryDao dao=mock(InstitutionStudentDirectoryDao.class);
 final OrganizationMapper organizations=mock(OrganizationMapper.class);
 final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
 final FeatureAccessService features=mock(FeatureAccessService.class);
 final AuthenticatedUser actor=mock(AuthenticatedUser.class);
 final Organization school=Organization.create(10L,null,"ROOT","school","School","SCHOOL","/school/",1,null);
 final Organization grade=Organization.create(11L,10L,"PARENT:10","grade","Grade","GRADE","/school/grade/",1,null);
 final Organization classroom=Organization.create(12L,11L,"PARENT:11","class","Class","CLASS","/school/grade/class/",1,null);
 final InstitutionStudentDirectoryService service=new InstitutionStudentDirectoryService(dao,organizations,access,features);
 @BeforeEach void setup(){when(organizations.findById(10L)).thenReturn(school);when(organizations.findById(11L)).thenReturn(grade);when(organizations.findById(12L)).thenReturn(classroom);when(organizations.findAll()).thenReturn(List.of(school,grade,classroom));when(access.scope(actor)).thenReturn(OrganizationDataScope.all(false));}
 @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();}
 @Test void rejectsCrossInstitutionSelectedContext(){var request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,Organization.create(20L,null,"ROOT","other","Other","SCHOOL","/other/",1,null));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));assertThatThrownBy(()->service.options(actor,10L)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(dao);}
 @Test void rejectsFamilyContextEvenWithGlobalScope(){var request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,Organization.create(20L,null,"ROOT","family","Family","FAMILY","/family/",1,null));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));assertThatThrownBy(()->service.options(actor,10L)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(dao);}
 @Test void rejectsEmptyDataScope(){when(access.scope(actor)).thenReturn(OrganizationDataScope.empty());assertThatThrownBy(()->service.options(actor,10L)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(dao);}
 @Test void gradeScopeNeverBecomesInstitutionWide(){when(access.scope(actor)).thenReturn(new OrganizationDataScope(false,false,List.of(grade.path())));when(dao.page(any(),eq(0),eq(20))).thenReturn(List.of());service.list(actor,10L,null,null,null,"ALL",1,20);verify(dao).count(argThat(q->!q.institutionScope()&&q.classIds().equals(List.of(12L))));verify(access).require(actor,"STUDENT_READ");verify(features).requireEnabled("CLASS_MANAGEMENT",10L);}
 @Test void rejectsInactiveInstitution(){when(organizations.findById(10L)).thenReturn(new Organization(10L,null,"ROOT","school","School","SCHOOL","/school/",1,OrganizationStatus.DISABLED,OrganizationEffectiveStatus.DISABLED,1,null,null,null));assertThatThrownBy(()->service.options(actor,10L)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(dao);}
 @Test void rejectsForeignGradeBeforeQuery(){assertThatThrownBy(()->service.list(actor,10L,null,99L,null,"ALL",1,20)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(dao);}
 @Test void missingStudentDetailIs404(){when(dao.page(any(),eq(0),eq(1))).thenReturn(List.of());assertThatThrownBy(()->service.detail(actor,10L,99L)).isInstanceOf(ResourceNotFoundException.class);}
 @Test void rejectsInvalidPaginationAndStatus(){assertThatThrownBy(()->service.list(actor,10L,null,null,null,"ALL",0,20)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->service.list(actor,10L,null,null,null,"CANCELLED",1,20)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(dao);}
 @Test void rejectsForeignClassBeforeQuery(){assertThatThrownBy(()->service.list(actor,10L,null,null,99L,"ALL",1,20)).isInstanceOf(ResourceNotFoundException.class);verifyNoInteractions(dao);}
 @Test void permissionDenialStopsAllQueries(){doThrow(new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("无权读取学员")).when(access).require(actor,"STUDENT_READ");assertThatThrownBy(()->service.options(actor,10L)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);verifyNoInteractions(dao,organizations,features);}
 @Test void inactiveClassNeverAppearsInOptions(){var inactive=new Organization(12L,11L,"PARENT:11","class","Class","CLASS",classroom.path(),1,OrganizationStatus.DISABLED,OrganizationEffectiveStatus.DISABLED,1,null,null,null);when(organizations.findAll()).thenReturn(List.of(school,grade,inactive));assertThat(service.options(actor,10L).classes()).isEmpty();}}
