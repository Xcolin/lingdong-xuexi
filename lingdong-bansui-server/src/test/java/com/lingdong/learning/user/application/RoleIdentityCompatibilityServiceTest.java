package com.lingdong.learning.user.application;
import com.lingdong.learning.iam.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.application.*;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.permission.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class RoleIdentityCompatibilityServiceTest {
 final RoleMapper roles=mock(RoleMapper.class);final RolePermissionMapper grants=mock(RolePermissionMapper.class);final PermissionMapper permissions=mock(PermissionMapper.class);final PermissionAvailabilityService availability=mock(PermissionAvailabilityService.class);
 final RoleIdentityCompatibilityService service=new RoleIdentityCompatibilityService(roles,grants,permissions,availability);
 final Permission action=new Permission(10L,"REWARD_MANAGE_CHILD","Family action",PermissionResourceType.OPERATION,PermissionClient.WEB,null,PermissionStatus.DISABLED,null);
 void fixture(){when(grants.lockRole(20L)).thenReturn(20L);when(roles.findById(20L)).thenReturn(Role.custom(20L,"FAMILY_CUSTOM","Family custom",null,RoleDataScope.SELF));when(grants.lockByRoleId(20L)).thenReturn(List.of(new PermissionAssignment(10L,PermissionEffect.ALLOW)));when(permissions.findById(10L)).thenReturn(action);}
 @Test void refusesIncompatibleIdentityBeforeRoleGrant(){fixture();when(availability.identityReason(action,1L,"FAMILY_CUSTOM")).thenReturn("账号身份不适用：FAMILY");assertThatThrownBy(()->service.requireCompatible(1L,20L)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class).hasMessageContaining("REWARD_MANAGE_CHILD");}
 @Test void temporaryDisabledPermissionFeatureAndMenuDoNotPreventCompatibleAssociation(){fixture();service.requireCompatible(1L,20L);verify(availability).identityReason(action,1L,"FAMILY_CUSTOM");verify(availability,never()).generalReason(any());verify(availability,never()).userReason(any(),anyLong());}
 @Test void deniedActionsDoNotRestrictTargetIdentity(){fixture();when(grants.lockByRoleId(20L)).thenReturn(List.of(new PermissionAssignment(10L,PermissionEffect.DENY)));service.requireCompatible(1L,20L);verifyNoInteractions(availability,permissions);}
}
