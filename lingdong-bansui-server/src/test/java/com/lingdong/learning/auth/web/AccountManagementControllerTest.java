package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import com.lingdong.learning.organization.domain.Organization;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
class AccountManagementControllerTest {
 final UserMapper users=mock(UserMapper.class);final UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);final PermissionDecisionService permissions=mock(PermissionDecisionService.class);final OrganizationDataScopeService scope=mock(OrganizationDataScopeService.class);final AuthenticationApplicationService auth=mock(AuthenticationApplicationService.class);final InitialPasswordStateService initial=mock(InitialPasswordStateService.class);
 final AccountManagementController controller=new AccountManagementController(users,memberships,permissions,scope,auth,initial);
 final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"admin","Admin",AuthClientType.MINIAPP,List.of());
 @BeforeEach void selected(){var request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,Organization.create(10L,null,"ROOT","x","Org","FAMILY","/x/",0,null));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"IAM_USER_PASSWORD_SET")).thenReturn(true);when(scope.canAccess(1L,10L)).thenReturn(true);}
 @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();}
 @Test void rejectsExplicitPermissionDenialBeforeAnyMutation(){when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"IAM_USER_PASSWORD_SET")).thenReturn(false);assertThatThrownBy(()->controller.reset(actor,3L,new AccountManagementController.Reset(10L,"Pass1234"))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(users,auth,initial);}
 @Test void rejectsOrganizationDifferentFromSelectedEvenWhenScopeAllows(){when(scope.canAccess(1L,11L)).thenReturn(true);assertThatThrownBy(()->controller.reset(actor,3L,new AccountManagementController.Reset(11L,"Pass1234"))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(users,auth,initial);}
 @Test void rejectsExternalMember(){when(users.findByIdForUpdate(3L)).thenReturn(User.create(3L,"family","Family",null,UserType.FAMILY));when(memberships.findOrganizationIds(3L)).thenReturn(List.of(11L));assertThatThrownBy(()->controller.reset(actor,3L,new AccountManagementController.Reset(10L,"Pass1234"))).isInstanceOf(SystemOperationAccessDeniedException.class);verify(users,never()).updatePasswordHash(anyLong(),anyString());verifyNoInteractions(auth,initial);}
 @Test void rejectsStudentEvenWhenMember(){when(users.findByIdForUpdate(3L)).thenReturn(User.create(3L,"student","Student",null,UserType.STUDENT));assertThatThrownBy(()->controller.reset(actor,3L,new AccountManagementController.Reset(10L,"Pass1234"))).isInstanceOf(SystemOperationAccessDeniedException.class);verifyNoInteractions(auth,initial);}
 @Test void validMemberResetMarksInitialPasswordAndRevokesSessions(){when(users.findByIdForUpdate(3L)).thenReturn(User.create(3L,"family","Family",null,UserType.FAMILY));when(memberships.findOrganizationIds(3L)).thenReturn(List.of(10L));when(auth.encodeManagedUserPassword("Pass1234")).thenReturn("hash");when(users.updatePasswordHash(3L,"hash")).thenReturn(1);controller.reset(actor,3L,new AccountManagementController.Reset(10L,"Pass1234"));verify(initial).require(3L);verify(auth).revokeAllActiveSessionsForUser(3L);}
}
