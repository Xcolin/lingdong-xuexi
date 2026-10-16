package com.lingdong.learning.user.application;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
class UserOrganizationEditServiceTest {
 UserAccessApplicationService access=mock(UserAccessApplicationService.class);UserMapper users=mock(UserMapper.class);UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);OrganizationMapper orgs=mock(OrganizationMapper.class);MemberScopeService scopes=mock(MemberScopeService.class);PermissionDecisionService decisions=mock(PermissionDecisionService.class);UserOrganizationEditMapper guards=mock(UserOrganizationEditMapper.class);
 UserOrganizationEditService service=new UserOrganizationEditService(access,users,memberships,orgs,scopes,decisions,guards);
 @BeforeEach void setup(){var user=User.create(2L,"u","用户",null,UserType.PLATFORM);when(users.findById(2L)).thenReturn(user);when(users.findByIdForUpdate(2L)).thenReturn(user);when(decisions.isAllowed(eq(1L),any(),anyString())).thenReturn(true);when(memberships.findOrganizationIds(2L)).thenReturn(List.of(10L));when(guards.protectedOrganizationIds(2L)).thenReturn(List.of());when(scopes.requireTarget(1L,20L)).thenReturn(Organization.create(20L,null,"ROOT","CITY20","城市","CITY","/20/",0,null));}
 @Test void replacesOnlyRequestedMembershipAndUpdatesProfile(){service.update(1L,2L,"用户",null,List.of(20L));verify(access).associateWithOrganization(new AssociateUserWithOrganizationCommand(2L,20L,1L));verify(access).dissociateFromOrganization(new AssociateUserWithOrganizationCommand(2L,10L,1L));verify(access).updateProfile(1L,2L,"用户",null);}
 @Test void cannotRemoveLastOrganization(){assertThatThrownBy(()->service.update(1L,2L,"用户",null,List.of())).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(access);}
 @Test void cannotRemoveBusinessDependentOrganization(){when(guards.protectedOrganizationIds(2L)).thenReturn(List.of(10L));assertThatThrownBy(()->service.update(1L,2L,"用户",null,List.of(20L))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("业务关系");verifyNoInteractions(access);}
 @Test void updatePermissionAloneCanChangeScopedOrganization(){when(decisions.isAllowed(1L,com.lingdong.learning.permission.domain.PermissionClient.WEB,"IAM_USER_ORGANIZATION_ASSIGN")).thenReturn(false);service.update(1L,2L,"用户",null,List.of(20L));verify(access).associateWithOrganization(new AssociateUserWithOrganizationCommand(2L,20L,1L));}
 @Test void rejectsStaleMembershipSnapshot(){assertThatThrownBy(()->service.update(1L,2L,"用户",null,List.of(20L),List.of(30L))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已变化");verifyNoInteractions(access);}
}
