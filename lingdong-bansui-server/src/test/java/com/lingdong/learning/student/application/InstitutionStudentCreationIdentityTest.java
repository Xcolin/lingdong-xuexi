package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InstitutionStudentCreationIdentityTest {
 final UserMapper users=mock(UserMapper.class);
 final StudentActorIdentityService actors=new StudentActorIdentityService(users,mock(ParentActorAccessService.class));
 final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
 final StudentIdentityProvisioningService provision=mock(StudentIdentityProvisioningService.class);
 final OrganizationMapper orgs=mock(OrganizationMapper.class);
 final StudentMapper students=mock(StudentMapper.class);
 final StudentOrganizationMapper relations=mock(StudentOrganizationMapper.class);
 final GrowthPointAccountMapper points=mock(GrowthPointAccountMapper.class);
 final GrowthPointLifecycleMapper cycles=mock(GrowthPointLifecycleMapper.class);
 final IdGenerator ids=mock(IdGenerator.class);
 final StudentApplicationService service=new StudentApplicationService(students,mock(ParentStudentMapper.class),relations,orgs,mock(OrganizationAdminMapper.class),provision,points,cycles,ids,access,actors);
 final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
 void setup(UserType type){when(users.findById(1L)).thenReturn(User.create(1L,"actor","actor",null,type));when(orgs.findById(3L)).thenReturn(Organization.create(3L,null,"ROOT","S","school","SCHOOL","/3/",1,null));when(access.organizationAllowed(actor,3L)).thenReturn(true);when(ids.nextId()).thenReturn(100L);when(provision.issue("child")).thenReturn(new IssuedStudentCredential(200L,"childAccount","1107"));when(points.insertInitial(100L)).thenReturn(1);when(cycles.insertDormancyState(100L)).thenReturn(1);when(students.findById(100L)).thenReturn(Student.create(100L,"child",null,200L));}
 @ParameterizedTest @EnumSource(value=UserType.class,names={"PERSONAL","FAMILY","ORGANIZATION","PLATFORM"}) void grantedScopedAdultAccountCanCreateInstitutionChild(UserType type){setup(type);service.createStudent(actor,new CreateStudentCommand("child",null,3L));verify(access).require(actor,"STUDENT_CREATE");verify(relations).insertEnrollment(100L,100L,3L);}
 @Test void familyIdentityDoesNotBypassScope(){setup(UserType.FAMILY);when(access.organizationAllowed(actor,3L)).thenReturn(false);assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child",null,3L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);verify(access).organizationAllowed(actor,3L);}
 @Test void studentIdentityCannotManageAccountsDespiteGrants(){setup(UserType.STUDENT);assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child",null,3L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);}
 @Test void disabledAccountCannotManageAccounts(){setup(UserType.FAMILY);when(users.findById(1L)).thenReturn(new User(1L,"actor","actor",null,null,UserType.FAMILY,UserStatus.DISABLED,null,null));assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child",null,3L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);}
 @Test void permissionDeniedBeforeProvisioning(){setup(UserType.PERSONAL);doThrow(new SystemOperationAccessDeniedException("denied")).when(access).require(actor,"STUDENT_CREATE");assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child",null,3L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision,users);}
 @Test void institutionPathCannotBypassFamilyGuardianCreation(){setup(UserType.ORGANIZATION);when(orgs.findById(3L)).thenReturn(Organization.create(3L,null,"ROOT","F","family","FAMILY","/3/",1,null));assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child",null,3L))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(provision);}
 @Test void classAssignmentDeniedBeforeAnyAccountProvisioning(){setup(UserType.FAMILY);when(orgs.findById(4L)).thenReturn(Organization.create(4L,3L,"S","C","class","CLASS","/3/4/",1,null));when(access.organizationAllowed(actor,4L)).thenReturn(true);doThrow(new SystemOperationAccessDeniedException("denied")).when(access).require(actor,"STUDENT_CLASS_ASSIGN");assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child",null,4L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);verify(access).require(actor,"STUDENT_CLASS_ASSIGN");}
}
