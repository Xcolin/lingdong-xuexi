package com.lingdong.learning.growthpoint.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiniappParentRewardIdentityTest {
    final UserMapper users=mock(UserMapper.class);
    final ParentAuthenticationMapper profiles=mock(ParentAuthenticationMapper.class);
    final UserRoleMapper roles=mock(UserRoleMapper.class);
    final ParentActorAccessService parents=new ParentActorAccessService(users,profiles,roles);
    final AuthenticatedUser mini=new AuthenticatedUser(1L,2L,"custom","custom",AuthClientType.MINIAPP,List.of("CUSTOM_FAMILY","SYS_AUDITOR"));
    @Test void customProfileParentIsAcceptedWithoutRoleNameRestriction() {
        when(users.findById(1L)).thenReturn(User.create(1L,"custom","custom",null,UserType.FAMILY));
        when(profiles.findProfileByUserId(1L)).thenReturn(mock(ParentProfile.class));
        when(roles.hasAnyEnabledRole(1L)).thenReturn(true);
        assertThatCode(() -> ParentRewardController.requireMiniappParent(mini,parents)).doesNotThrowAnyException();
    }
    @Test void webClientAndNonFamilyRoleHolderAreRejected() {
        var web=new AuthenticatedUser(1L,2L,"custom","custom",AuthClientType.WEB,List.of("PARENT"));
        assertThatThrownBy(() -> ParentRewardController.requireMiniappParent(web,parents)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(users.findById(1L)).thenReturn(User.create(1L,"custom","custom",null,UserType.PLATFORM));
        assertThatThrownBy(() -> ParentRewardController.requireMiniappParent(mini,parents)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
}
