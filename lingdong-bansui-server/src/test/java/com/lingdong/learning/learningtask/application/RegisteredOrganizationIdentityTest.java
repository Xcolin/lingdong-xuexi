package com.lingdong.learning.learningtask.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RegisteredOrganizationIdentityTest {
    @Test void registeredAccountBecomesOrganizationOperatorOnlyAfterActualAdministratorRole() {
        var users=mock(UserMapper.class);var roles=mock(UserRoleMapper.class);
        when(users.findById(10L)).thenReturn(User.create(10L,"registered","用户",null,UserType.PERSONAL));
        var service=new LearningTaskScopeService(null,null,null,null,null,users,roles,null,null,null,null);
        var actor=new AuthenticatedUser(10L,20L,"registered","用户",AuthClientType.WEB,List.of());
        assertThat(service.isOrganizationOperator(actor)).isFalse();
        when(roles.hasRoleCode(10L,"ORG_ADMIN")).thenReturn(true);
        assertThat(service.isOrganizationOperator(actor)).isTrue();
    }
    @Test void disabledRegisteredAdministratorDoesNotBecomeOrganizationOperator() {
        var users=mock(UserMapper.class);var roles=mock(UserRoleMapper.class);
        when(users.findById(10L)).thenReturn(new User(10L,"registered","用户",null,null,UserType.PERSONAL,UserStatus.DISABLED,null,null));
        when(roles.hasRoleCode(10L,"ORG_ADMIN")).thenReturn(true);
        var service=new LearningTaskScopeService(null,null,null,null,null,users,roles,null,null,null,null);
        assertThat(service.isOrganizationOperator(new AuthenticatedUser(10L,20L,"registered","用户",AuthClientType.WEB,List.of()))).isFalse();
    }
}
