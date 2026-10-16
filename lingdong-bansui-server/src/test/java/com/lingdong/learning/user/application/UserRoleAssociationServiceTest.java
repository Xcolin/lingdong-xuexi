package com.lingdong.learning.user.application;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class UserRoleAssociationServiceTest {
 UserAccessApplicationService access=mock(UserAccessApplicationService.class);UserMapper users=mock(UserMapper.class);RoleMapper roles=mock(RoleMapper.class);UserRoleAssociationMapper relations=mock(UserRoleAssociationMapper.class);MemberScopeService scopes=mock(MemberScopeService.class);PermissionDecisionService decisions=mock(PermissionDecisionService.class);
 UserRoleAssociationService service=new UserRoleAssociationService(access,users,roles,relations,scopes,decisions);
 RoleIdentityCompatibilityService identity=mock(RoleIdentityCompatibilityService.class);
 @BeforeEach void setup(){org.springframework.test.util.ReflectionTestUtils.setField(service,"roleIdentityCompatibility",identity);var user=User.create(2L,"u","用户",null,UserType.PLATFORM);when(users.findById(2L)).thenReturn(user);when(users.findByIdForUpdate(2L)).thenReturn(user);when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_ROLE_ASSIGN")).thenReturn(true);}
 @Test void assignsRolesThroughExistingAuthorizedBatchUseCase(){service.change(1L,2L,List.of(new UserRoleAssociationService.Item(10L,null)),false);verify(access).batchAssignRole(new BatchAssignRoleToRoleCommand(1L,10L,List.of(new BatchRoleAssignmentItem(2L,null))));}
 @Test void removesExactOrganizationGrantThroughProtectedUseCase(){service.change(1L,2L,List.of(new UserRoleAssociationService.Item(10L,20L)),true);verify(access).batchRemoveRole(new BatchAssignRoleToRoleCommand(1L,10L,List.of(new BatchRoleAssignmentItem(2L,20L))));}
 @Test void duplicateAssignmentsRejectedBeforeChanges(){var item=new UserRoleAssociationService.Item(10L,null);assertThatThrownBy(()->service.change(1L,2L,List.of(item,item),false)).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(access);}
 @Test void unauthorizedActorCannotReadOrChangeRoles(){when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_ROLE_ASSIGN")).thenReturn(false);assertThatThrownBy(()->service.context(1L,2L)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);assertThatThrownBy(()->service.change(1L,2L,List.of(new UserRoleAssociationService.Item(10L,null)),false)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);verifyNoInteractions(access);}
 @Test void roleAssociationContextOmitsAssignmentsOutsideActualOrganizationScope(){when(scopes.orderedAccessible(1L,null)).thenReturn(List.of());when(scopes.all(1L)).thenReturn(false);when(roles.findAll()).thenReturn(List.of());when(relations.findByUserId(2L)).thenReturn(List.of(new UserRoleAssociationMapper.Assignment(10L,99L)));assertThat(service.context(1L,2L).assignments()).isEmpty();}
 @Test void identityIncompatibleRoleRejectedBeforeAnyAssignment(){doThrow(new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("身份不适用")).when(identity).requireCompatible(2L,10L);assertThatThrownBy(()->service.change(1L,2L,List.of(new UserRoleAssociationService.Item(10L,null)),false)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);verifyNoInteractions(access);}
 @Test void incompatibleHistoricalRoleCanStillBeRemoved(){service.change(1L,2L,List.of(new UserRoleAssociationService.Item(10L,null)),true);verifyNoInteractions(identity);verify(access).batchRemoveRole(any());}
}
