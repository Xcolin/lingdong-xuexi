package com.lingdong.learning.permission.application;
import com.lingdong.learning.permission.infrastructure.persistence.*;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class TransferNecessaryReadDecisionTest {
 PermissionMapper catalog=mock(PermissionMapper.class);RolePermissionMapper roles=mock(RolePermissionMapper.class);UserPermissionMapper own=mock(UserPermissionMapper.class);UserRoleMapper userRoles=mock(UserRoleMapper.class);UserMapper users=mock(UserMapper.class);PermissionAvailabilityService availability=mock(PermissionAvailabilityService.class);
 PermissionDecisionService decision=new PermissionDecisionService(catalog,roles,own,userRoles,users);
 Permission action=new Permission(10L,"STUDENT_TRANSFER_REVIEW","review",PermissionResourceType.OPERATION,PermissionClient.BOTH,null,PermissionStatus.ENABLED,null),read=new Permission(11L,"STUDENT_TRANSFER_VIEW","read",PermissionResourceType.OPERATION,PermissionClient.BOTH,null,PermissionStatus.ENABLED,null);
 @BeforeEach void setup(){ReflectionTestUtils.setField(decision,"availability",availability);when(users.findById(1L)).thenReturn(User.create(1L,"fixture","fixture",null,UserType.FAMILY));when(userRoles.hasAnyEnabledRole(1L)).thenReturn(true);when(catalog.findAll()).thenReturn(List.of(action,read));when(catalog.findAllowedCodes(1L,PermissionClient.MINIAPP)).thenReturn(List.of(action.code()));when(availability.miniappImpliedReads(action.code())).thenReturn(Set.of(read.code()));}
 @Test void reviewGrantIncludesOnlyDeclaredNecessaryRead(){assertEquals(Set.of(action.code(),read.code()),new HashSet<>(decision.findAllowedCodes(1L,PermissionClient.MINIAPP)));}
 @Test void explicitReadDenyBlocksActionAndRead(){when(own.findByUserId(1L)).thenReturn(List.of(new PermissionAssignment(read.id(),PermissionEffect.DENY)));assertTrue(decision.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
 @Test void disabledNecessaryReadBlocksAction(){when(catalog.findAll()).thenReturn(List.of(action,new Permission(11L,read.code(),"read",PermissionResourceType.OPERATION,PermissionClient.BOTH,null,PermissionStatus.DISABLED,null)));assertTrue(decision.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
 @Test void unavailableNecessaryReadBlocksAction(){when(availability.userReason(read,1L,PermissionClient.MINIAPP)).thenReturn("fixture unavailable");assertTrue(decision.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
 @Test void unrelatedMiniappActionsDoNotInheritWebReads(){when(availability.miniappImpliedReads(action.code())).thenReturn(Set.of());when(availability.impliedReads(action.code(),1L)).thenReturn(Set.of(read.code()));assertEquals(List.of(action.code()),decision.findAllowedCodes(1L,PermissionClient.MINIAPP));}
 @Test void roleDenyOnReadBlocksAction(){when(roles.findInheritedByUserId(1L)).thenReturn(List.of(new PermissionAssignment(read.id(),PermissionEffect.DENY)));assertTrue(decision.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
 @Test void absentNecessaryReadBlocksAction(){when(catalog.findAll()).thenReturn(List.of(action));assertTrue(decision.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
 @Test void webOnlyReadCannotEnableMiniappAction(){when(catalog.findAll()).thenReturn(List.of(action,new Permission(11L,read.code(),"read",PermissionResourceType.OPERATION,PermissionClient.WEB,null,PermissionStatus.ENABLED,null)));assertTrue(decision.findAllowedCodes(1L,PermissionClient.MINIAPP).isEmpty());}
 @Test void realPolicyLimitsMiniappExpansionToTransferActions()throws Exception{
  var policy=new PermissionAvailabilityService(new com.fasterxml.jackson.databind.ObjectMapper(),mock(com.lingdong.learning.feature.application.FeatureAccessService.class),mock(com.lingdong.learning.menu.infrastructure.persistence.MenuMapper.class),users,mock(com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper.class),userRoles,mock(com.lingdong.learning.iam.infrastructure.persistence.RoleMapper.class));
  assertEquals(Set.of("STUDENT_TRANSFER_VIEW"),policy.miniappImpliedReads("STUDENT_TRANSFER_REVIEW"));assertEquals(Set.of("STUDENT_TRANSFER_VIEW"),policy.miniappImpliedReads("STUDENT_TRANSFER_REQUEST_CREATE"));assertTrue(policy.miniappImpliedReads("LEARNING_TASK_CREATE").isEmpty());
 }
}

