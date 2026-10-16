package com.lingdong.learning.student.application;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentCreationIdentityScopeTest {
    final UserMapper users=mock(UserMapper.class);
    final ParentActorAccessService parents=new ParentActorAccessService(users,mock(ParentAuthenticationMapper.class),mock(UserRoleMapper.class));
    final StudentActorIdentityService actors=new StudentActorIdentityService(users,parents);
    final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
    final StudentIdentityProvisioningService provision=mock(StudentIdentityProvisioningService.class);
    final OrganizationMapper organizations=mock(OrganizationMapper.class);
    final StudentApplicationService service=new StudentApplicationService(mock(StudentMapper.class),mock(ParentStudentMapper.class),
            mock(StudentOrganizationMapper.class),organizations,mock(OrganizationAdminMapper.class),provision,
            mock(GrowthPointAccountMapper.class),mock(GrowthPointLifecycleMapper.class),mock(IdGenerator.class),access,actors);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.WEB,List.of("PARENT","ORG_ADMIN"));
    @Test void roleNamesCannotCreateFamilyStudentWithoutActualFamilyProfile() {
        when(users.findById(1L)).thenReturn(User.create(1L,"actor","actor",null,UserType.ORGANIZATION));
        assertThatThrownBy(() -> service.createStudent(actor,new CreateStudentCommand("child","G3",null)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        verify(access).require(actor,"STUDENT_CREATE");verifyNoInteractions(provision);
    }
    @Test void institutionStudentCreationCannotEscapeSchoolScope() {
        when(users.findById(1L)).thenReturn(User.create(1L,"actor","actor",null,UserType.ORGANIZATION));
        when(organizations.findById(3L)).thenReturn(Organization.create(3L,null,"ROOT","SCHOOL","school","SCHOOL","/SCHOOL/",1,null));
        assertThatThrownBy(() -> service.createStudent(actor,new CreateStudentCommand("child","G3",3L)))
                .isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(provision);
    }
}
