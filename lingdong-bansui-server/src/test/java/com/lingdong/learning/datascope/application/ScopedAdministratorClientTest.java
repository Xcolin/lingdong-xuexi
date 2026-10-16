package com.lingdong.learning.datascope.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.infrastructure.persistence.*;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ScopedAdministratorClientTest {
    final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    final UserMapper users=mock(UserMapper.class);
    final OrganizationMapper organizations=mock(OrganizationMapper.class);
    final UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
    final OrganizationAdminMapper administrators=mock(OrganizationAdminMapper.class);
    final MemberScopeService scope=mock(MemberScopeService.class);
    final DataScopeAdministrationService service=new DataScopeAdministrationService(mock(UserRoleMapper.class),users,organizations,memberships,administrators,mock(RoleMapper.class),mock(RoleDataScopeMapper.class),mock(IdGenerator.class),mock(IamChangeAuditService.class),permissions);
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"memberScopes",scope);
        when(users.findById(2L)).thenReturn(User.create(2L,"member","member",null,UserType.ORGANIZATION));
        when(organizations.findById(10L)).thenReturn(Organization.create(10L,null,"ROOT","S","school","SCHOOL","/S/",0,null));
        when(memberships.exists(2L,10L)).thenReturn(true);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void actor(AuthClientType client){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(1L,2L,"operator","operator",client,List.of("ORG_ADMIN")),null,List.of()));}
    @Test void miniappAssignmentAndRevocationUseTheActualClientAndKeepScopeChecks(){
        actor(AuthClientType.MINIAPP);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"ORG_ADMIN_ASSIGN")).thenReturn(true);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"ORG_ADMIN_REVOKE")).thenReturn(true);
        service.configureScopedOrganizationAdministrator(1L,2L,10L);
        when(administrators.exists(2L,10L)).thenReturn(true);
        when(administrators.delete(10L,2L)).thenReturn(1);
        service.revokeScopedOrganizationAdministrator(1L,2L,10L);
        verify(scope,times(2)).requireTarget(1L,10L);
        verify(permissions,never()).isAllowed(eq(1L),eq(PermissionClient.WEB),anyString());
    }
    @Test void webRequestCannotBorrowAMiniappOnlyGrant(){
        actor(AuthClientType.WEB);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"ORG_ADMIN_ASSIGN")).thenReturn(true);
        assertThatThrownBy(()->service.configureScopedOrganizationAdministrator(1L,2L,10L)).isInstanceOf(SystemOperationAccessDeniedException.class);
        verifyNoInteractions(administrators);
    }
}
