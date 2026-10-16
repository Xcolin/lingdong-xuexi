package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentCredentialIdentityScopeTest {
    final UserMapper users=mock(UserMapper.class);
    final UserRoleMapper roles=mock(UserRoleMapper.class);
    final ParentAuthenticationMapper profiles=mock(ParentAuthenticationMapper.class);
    final StudentActorIdentityService actors=new StudentActorIdentityService(users,new ParentActorAccessService(users,profiles,roles));
    final StudentMapper students=mock(StudentMapper.class);
    final ParentStudentMapper guardians=mock(ParentStudentMapper.class);
    final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
    final StudentIdentityProvisioningService provision=mock(StudentIdentityProvisioningService.class);
    final StudentOrganizationMapper organizations=mock(StudentOrganizationMapper.class);
    final StudentCredentialManagementService service=new StudentCredentialManagementService(students,guardians,
            organizations,provision,mock(AuthenticationApplicationService.class),users,access,actors);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"custom","custom",AuthClientType.WEB,List.of("CUSTOM_FAMILY","SYS_AUDITOR"));
    void parent() {
        when(users.findById(1L)).thenReturn(User.create(1L,"custom","custom",null,UserType.FAMILY));
        when(profiles.findProfileByUserId(1L)).thenReturn(mock(ParentProfile.class));
        when(roles.hasAnyEnabledRole(1L)).thenReturn(true);
        when(students.findById(3L)).thenReturn(Student.create(3L,"child","G3",null));
    }
    @Test void customPrimaryParentInitializesUsingOnlyItsExactAction() {
        parent();when(guardians.existsActivePrimaryByParentAndStudent(1L,3L)).thenReturn(true);
        when(provision.issue("child")).thenReturn(new IssuedStudentCredential(4L,"student","1234"));
        when(students.bindStudentUserIfAbsent(3L,4L)).thenReturn(1);
        assertThat(service.initialize(actor,3L).studentAccount()).isEqualTo("student");
        verify(access).require(actor,"STUDENT_CREDENTIAL_INITIALIZE");
    }
    @Test void familyAdministratorCanManageCredentialWithoutImplicitGuardianCreation() {
        parent();when(organizations.existsFamilyAdministratorForStudent(3L,1L)).thenReturn(true);
        when(provision.issue("child")).thenReturn(new IssuedStudentCredential(4L,"student","1234"));
        when(students.bindStudentUserIfAbsent(3L,4L)).thenReturn(1);
        assertThat(service.initialize(actor,3L).studentAccount()).isEqualTo("student");
        verify(access).require(actor,"STUDENT_CREDENTIAL_INITIALIZE");
    }
    @Test void unrelatedFamilyCannotUseInstitutionScopeToBypassPrimaryRelationship() {
        parent();when(access.studentAllowed(actor,3L)).thenReturn(true);
        assertThatThrownBy(() -> service.initialize(actor,3L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(provision);
    }
    @Test void customInstitutionOperatorCannotChangeStudentOutsideSchoolScope() {
        when(users.findById(1L)).thenReturn(User.create(1L,"custom_org","org",null,UserType.ORGANIZATION));
        when(students.findById(3L)).thenReturn(Student.create(3L,"child","G3",null));
        assertThatThrownBy(() -> service.initialize(actor,3L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(provision);
    }
}
