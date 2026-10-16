package com.lingdong.learning.permission.application;
import com.lingdong.learning.permission.infrastructure.persistence.*;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.menu.domain.*;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class OrganizationPagePermissionDecisionTest {
 PermissionMapper permissions=mock(PermissionMapper.class);RolePermissionMapper roles=mock(RolePermissionMapper.class);UserPermissionMapper own=mock(UserPermissionMapper.class);UserRoleMapper userRoles=mock(UserRoleMapper.class);UserMapper users=mock(UserMapper.class);MenuMapper menus=mock(MenuMapper.class);
 PermissionDecisionService service=new PermissionDecisionService(permissions,roles,own,userRoles,users);
 @BeforeEach void setup(){ReflectionTestUtils.setField(service,"menus",menus);when(userRoles.hasAnyEnabledRole(1L)).thenReturn(true);when(permissions.findAll()).thenReturn(List.of(new Permission(10L,"ORG_NODE_READ","组织管理",PermissionResourceType.PAGE,PermissionClient.WEB,null,PermissionStatus.ENABLED,null),new Permission(11L,"organizations.members.query","查询",PermissionResourceType.OPERATION,PermissionClient.WEB,null,PermissionStatus.ENABLED,null)));when(users.findById(1L)).thenReturn(User.create(1L,"parent","家长",null,UserType.FAMILY));when(permissions.findByCode("ORG_NODE_READ")).thenReturn(new Permission(10L,"ORG_NODE_READ","组织管理",PermissionResourceType.PAGE,PermissionClient.WEB,null,PermissionStatus.ENABLED,null));when(permissions.findAllowedCodes(1L,PermissionClient.WEB)).thenReturn(List.of("organizations.members.query"));when(menus.findAll()).thenReturn(List.of(new MenuNode(20L,"ORG_NODE_READ","组织管理",MenuType.PAGE,null,"/organizations",null,"ORG_NODE_READ",true,0,MenuStatus.ENABLED,1L),new MenuNode(21L,"organizations.members.query","组织用户查询",MenuType.BUTTON,20L,null,null,"organizations.members.query",true,10,MenuStatus.ENABLED,1L)));}
 @Test void childActionAllowsScopedOrganizationWorkspaceReadOnly(){assertThat(service.findAllowedCodes(1L,PermissionClient.WEB)).contains("ORG_NODE_READ").doesNotContain("ORG_NODE_UPDATE");assertThat(service.isAllowed(1L,PermissionClient.WEB,"ORG_NODE_READ")).isTrue();}
 @Test void explicitUserDenyWins(){when(own.findByUserId(1L)).thenReturn(List.of(new PermissionAssignment(10L,PermissionEffect.DENY)));assertThat(service.findAllowedCodes(1L,PermissionClient.WEB)).doesNotContain("ORG_NODE_READ");assertThat(service.isAllowed(1L,PermissionClient.WEB,"ORG_NODE_READ")).isFalse();}
 @Test void explicitRoleDenyWins(){when(roles.findInheritedByUserId(1L)).thenReturn(List.of(new PermissionAssignment(10L,PermissionEffect.DENY)));assertThat(service.findAllowedCodes(1L,PermissionClient.WEB)).doesNotContain("ORG_NODE_READ");assertThat(service.isAllowed(1L,PermissionClient.WEB,"ORG_NODE_READ")).isFalse();}
 @Test void unrelatedActionCannotOpenOrganizationPage(){when(permissions.findAllowedCodes(1L,PermissionClient.WEB)).thenReturn(List.of("OTHER_ACTION"));assertThat(service.findAllowedCodes(1L,PermissionClient.WEB)).doesNotContain("ORG_NODE_READ");}
 @Test void disabledMenuCannotOpenOrganizationPage(){when(menus.findAll()).thenReturn(List.of(new MenuNode(20L,"ORG_NODE_READ","组织管理",MenuType.PAGE,null,"/organizations",null,"ORG_NODE_READ",true,0,MenuStatus.DISABLED,1L)));assertThat(service.findAllowedCodes(1L,PermissionClient.WEB)).doesNotContain("ORG_NODE_READ");}
}
