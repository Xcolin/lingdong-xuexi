package com.lingdong.learning.menu.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.menu.domain.*;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.infrastructure.persistence.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.common.id.IdGenerator;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RegistrationMenuProjectionTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2})
    void myOrganizationsNavigationRequiresExplicitGrant(int grantKind) {
        boolean granted=grantKind!=0;
        MenuMapper mapper=mock(MenuMapper.class);
        PermissionDecisionService decisions=mock(PermissionDecisionService.class);
        var internal=new MenuNode(1L,"ORGANIZATION_ONBOARDING_READ","我的组织",MenuType.PAGE,null,"/my-organizations",null,"ORGANIZATION_ONBOARDING_READ",true,1,MenuStatus.ENABLED,0L);
        var action=new MenuNode(2L,"ORGANIZATION_ONBOARDING_SUBMIT","入驻",MenuType.BUTTON,1L,null,null,"ORGANIZATION_ONBOARDING_SUBMIT",true,1,MenuStatus.ENABLED,0L);
        var business=new MenuNode(3L,"DASHBOARD_READ","工作台",MenuType.PAGE,null,"/dashboard",null,"DASHBOARD_READ",true,2,MenuStatus.ENABLED,0L);
        when(mapper.findAll()).thenReturn(List.of(internal,action,business));
        when(decisions.findAllowedCodes(10L,PermissionClient.WEB)).thenReturn(List.of("ORGANIZATION_ONBOARDING_READ","ORGANIZATION_ONBOARDING_SUBMIT","DASHBOARD_READ"));
        PermissionMapper permissions=mock(PermissionMapper.class);
        when(permissions.findAllowedCodes(10L,PermissionClient.WEB)).thenReturn(grantKind==2?List.of("ORGANIZATION_ONBOARDING_SUBMIT"):granted?List.of("ORGANIZATION_ONBOARDING_READ"):List.of());
        var service=new MenuApplicationService(mapper,mock(IdGenerator.class),decisions,permissions,mock(UserMapper.class),mock(UserRoleMapper.class),mock(RolePermissionMapper.class),mock(UserPermissionMapper.class),mock(IamChangeAuditService.class));
        assertThat(service.current(new AuthenticatedUser(10L,20L,"user","用户",AuthClientType.WEB,List.of())))
            .extracting(MenuNode::id).containsExactlyElementsOf(granted?List.of(1L,2L,3L):List.of(3L));
        assertThat(mapper.findAll()).contains(internal,action);
    }
}
