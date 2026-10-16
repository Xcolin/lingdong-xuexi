package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.*;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class FamilyStudentCreationCompatibilityTest {
 final StudentMapper students=mock(StudentMapper.class);
 final ParentStudentMapper parents=mock(ParentStudentMapper.class);
 final OrganizationMapper organizations=mock(OrganizationMapper.class);
 final StudentIdentityProvisioningService provision=mock(StudentIdentityProvisioningService.class);
 final GrowthPointAccountMapper points=mock(GrowthPointAccountMapper.class);
 final GrowthPointLifecycleMapper lifecycle=mock(GrowthPointLifecycleMapper.class);
 final IdGenerator ids=mock(IdGenerator.class);
 final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
 final StudentActorIdentityService actors=mock(StudentActorIdentityService.class);
 final UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
 final StudentApplicationService service=new StudentApplicationService(students,parents,mock(StudentOrganizationMapper.class),organizations,mock(OrganizationAdminMapper.class),provision,points,lifecycle,ids,access,actors);
 final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"parent","parent",AuthClientType.MINIAPP,List.of("PARENT"));
 FamilyStudentCreationCompatibilityTest(){ReflectionTestUtils.setField(service,"memberships",memberships);}
 void family(){when(organizations.findByIdForUpdate(10L)).thenReturn(Organization.create(10L,null,"ROOT","F","family","FAMILY","/F/",1,null));when(access.organizationAllowed(actor,10L)).thenReturn(true);when(memberships.exists(1L,10L)).thenReturn(true);when(memberships.lockRelation(1L,10L)).thenReturn(50L);}
 void creation(){when(ids.nextId()).thenReturn(100L);when(provision.issue("child")).thenReturn(new IssuedStudentCredential(200L,"childAccount","code"));when(points.insertInitial(100L)).thenReturn(1);when(lifecycle.insertDormancyState(100L)).thenReturn(1);when(students.findById(100L)).thenReturn(Student.create(100L,"child","G3",200L));}
 @Test void ownFamilyCreatesRealMembershipAndPrimaryGuardian(){family();creation();when(memberships.insert(anyLong(),eq(200L),eq(10L))).thenReturn(1);service.createStudent(actor,new CreateStudentCommand("child","G3",null,10L));verify(memberships).insert(100L,200L,10L);verify(parents).insertPrimary(100L,1L,100L);verify(actors).requireParent(actor);}
 @Test void broadScopeCannotReplaceActualFamilyMembership(){family();when(memberships.exists(1L,10L)).thenReturn(false);assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child","G3",null,10L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);}
 @Test void actualMembershipStillRequiresScope(){family();when(access.organizationAllowed(actor,10L)).thenReturn(false);assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child","G3",null,10L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);}
 @Test void cannotSubmitInstitutionAndFamilyTogether(){assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child","G3",20L,10L))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(provision);}
 @Test void legacyRequestKeepsOriginalParentFlow(){creation();service.createStudent(actor,new CreateStudentCommand("child","G3",null));verifyNoInteractions(memberships,organizations);verify(parents).insertPrimary(100L,1L,100L);}
 @Test void failedMembershipPropagatesForTransactionRollback(){family();creation();assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child","G3",null,10L))).isInstanceOf(IllegalStateException.class);}
 @Test void disabledFamilyCannotCreateChild(){family();Organization disabled=mock(Organization.class);when(disabled.typeCode()).thenReturn("FAMILY");when(disabled.status()).thenReturn(OrganizationStatus.DISABLED);when(disabled.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.DISABLED);when(organizations.findByIdForUpdate(10L)).thenReturn(disabled);assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child","G3",null,10L))).isInstanceOf(IllegalStateException.class);verifyNoInteractions(provision);}
 @Test void disappearingMembershipCannotCreateChild(){family();when(memberships.lockRelation(1L,10L)).thenReturn(null);assertThatThrownBy(()->service.createStudent(actor,new CreateStudentCommand("child","G3",null,10L))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(provision);}

}
