package com.lingdong.learning.auth.application;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.*;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.student.application.*;
import com.lingdong.learning.student.domain.ParentRelationship;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentWechatBindingIdentityScopeTest {
    final UserMapper users=mock(UserMapper.class);
    final ParentAuthenticationMapper profiles=mock(ParentAuthenticationMapper.class);
    final UserRoleMapper roles=mock(UserRoleMapper.class);
    final StudentActorIdentityService actors=new StudentActorIdentityService(users,new ParentActorAccessService(users,profiles,roles));
    final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
    final StudentWechatBindingMapper bindings=mock(StudentWechatBindingMapper.class);
    final ParentStudentMapper guardians=mock(ParentStudentMapper.class);
    final StudentWechatBindingManagementService service=new StudentWechatBindingManagementService(bindings,guardians,
        mock(FeatureAccessService.class),mock(IdGenerator.class),Clock.systemUTC(),actors,access);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"custom","custom",AuthClientType.WEB,List.of("CUSTOM_FAMILY"));
    void parent() {
        when(users.findById(1L)).thenReturn(User.create(1L,"family","family",null,UserType.FAMILY));
        when(profiles.findProfileByUserId(1L)).thenReturn(mock(ParentProfile.class));
        when(roles.hasAnyEnabledRole(1L)).thenReturn(true);
    }
    @Test void customFamilyProfileCanListOnlyItsPrimaryStudents() {
        parent();assertThat(service.list(actor)).isEmpty();
        verify(bindings).findPrimaryStudentsByParentUserId(1L);
        verify(bindings,never()).findScopedStudents(any());
        verify(access).require(actor,"STUDENT_WECHAT_UNBIND");
    }
    @Test void familyCannotUnbindAnotherParentsStudentEvenWithScopeGrant() {
        parent();var primary=mock(ParentRelationship.class);when(primary.parentUserId()).thenReturn(99L);
        when(guardians.findActivePrimaryByStudentIdForUpdate(3L)).thenReturn(primary);
        when(access.schoolStudentAllowed(actor,3L)).thenReturn(true);
        assertThatThrownBy(() -> service.unbind(actor,3L,"确认解绑学生微信"))
            .isInstanceOf(StudentWechatBindingUnavailableException.class);
        verify(bindings,never()).findByStudentIdForUpdate(any());
    }
    @Test void institutionCannotUnbindStudentOutsideItsSchoolScope() {
        when(users.findById(1L)).thenReturn(User.create(1L,"org","org",null,UserType.ORGANIZATION));
        assertThatThrownBy(() -> service.unbind(actor,3L,"确认解绑学生微信"))
            .isInstanceOf(StudentWechatBindingUnavailableException.class);
        verify(bindings,never()).deleteById(any());
    }
}
