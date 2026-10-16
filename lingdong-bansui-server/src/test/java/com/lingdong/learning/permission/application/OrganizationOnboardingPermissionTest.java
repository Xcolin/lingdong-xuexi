package com.lingdong.learning.permission.application;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.permission.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OrganizationOnboardingPermissionTest {
 PermissionMapper catalog=mock(PermissionMapper.class);RolePermissionMapper roles=mock(RolePermissionMapper.class);UserPermissionMapper overrides=mock(UserPermissionMapper.class);UserRoleMapper memberships=mock(UserRoleMapper.class);UserMapper users=mock(UserMapper.class);
 PermissionDecisionService service=new PermissionDecisionService(catalog,roles,overrides,memberships,users);
 void account(UserType type){when(users.findById(1L)).thenReturn(User.create(1L,"user","User",null,type));when(catalog.findAll()).thenReturn(List.of(new Permission(3L,"ORGANIZATION_ONBOARDING_READ","入驻读取",PermissionResourceType.PAGE,PermissionClient.BOTH,null,PermissionStatus.ENABLED,null),new Permission(4L,"ORGANIZATION_ONBOARDING_SUBMIT","入驻申请",PermissionResourceType.OPERATION,PermissionClient.BOTH,null,PermissionStatus.ENABLED,null)));}
 @Test void nonStudentWithoutAnyRoleGetsOnlyPersonalOnboardingCapabilities(){account(UserType.ORGANIZATION);assertEquals(List.of("ORGANIZATION_ONBOARDING_READ","ORGANIZATION_ONBOARDING_SUBMIT"),service.findAllowedCodes(1L,PermissionClient.MINIAPP));verify(catalog,never()).findAllowedCodes(anyLong(),any());}
 @Test void explicitDenyStillWinsOverBootstrap(){account(UserType.FAMILY);when(overrides.findByUserId(1L)).thenReturn(List.of(new PermissionAssignment(4L,PermissionEffect.DENY)));assertEquals(List.of("ORGANIZATION_ONBOARDING_READ"),service.findAllowedCodes(1L,PermissionClient.MINIAPP));}
 @Test void studentNeverGetsBootstrap(){account(UserType.STUDENT);assertTrue(service.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
}
