package com.lingdong.learning.auth.application;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParentActorAccessServiceTest {
    final UserMapper users = mock(UserMapper.class);
    final ParentAuthenticationMapper profiles = mock(ParentAuthenticationMapper.class);
    final UserRoleMapper roles = mock(UserRoleMapper.class);
    final ParentActorAccessService access = new ParentActorAccessService(users, profiles, roles);
    final AuthenticatedUser actor = new AuthenticatedUser(1L, 2L, "family", "family", AuthClientType.WEB,
            List.of("CUSTOM_FAMILY", "SYS_AUDITOR"));
    void family() {
        when(users.findById(1L)).thenReturn(User.create(1L,"family","family",null,UserType.FAMILY));
        when(profiles.findProfileByUserId(1L)).thenReturn(mock(ParentProfile.class));
        when(roles.hasAnyEnabledRole(1L)).thenReturn(true);
    }
    @Test void acceptsCustomRoleAndMixedRoleWhenActualFamilyIdentityIsValid() {
        family(); assertThatCode(() -> access.require(actor)).doesNotThrowAnyException();
        verify(roles, never()).hasRoleCode(anyLong(), anyString());
    }
    @Test void rejectsRoleNameWithoutProfileAndRejectsInactiveRoles() {
        family(); when(profiles.findProfileByUserId(1L)).thenReturn(null);
        assertThatThrownBy(() -> access.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
        family(); when(roles.hasAnyEnabledRole(1L)).thenReturn(false);
        assertThatThrownBy(() -> access.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
    @Test void rejectsNonFamilyOrDisabledAccount() {
        family(); when(users.findById(1L)).thenReturn(User.create(1L,"admin","admin",null,UserType.PLATFORM));
        assertThatThrownBy(() -> access.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(users.findById(1L)).thenReturn(new User(1L,"family","family",null,null,UserType.FAMILY,UserStatus.DISABLED,null,null));
        assertThatThrownBy(() -> access.require(actor)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
