package com.lingdong.learning.user.application;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
class AdminUserEmailServiceTest {
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final MemberScopeService scopes=mock(MemberScopeService.class);
 final PermissionDecisionService decisions=mock(PermissionDecisionService.class);
 final UserMapper users=mock(UserMapper.class);
 final UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
 final AuthenticationApplicationService auth=mock(AuthenticationApplicationService.class);
 final IamChangeAuditService audit=mock(IamChangeAuditService.class);
 final AdminUserEmailService service=new AdminUserEmailService(jdbc,scopes,decisions,users,memberships,auth,audit);
 void permitted(){when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_UPDATE")).thenReturn(true);User user=mock(User.class);when(user.status()).thenReturn(UserStatus.ENABLED);when(users.findByIdForUpdate(2L)).thenReturn(user);when(memberships.findOrganizationIds(2L)).thenReturn(List.of(10L));}
 @Test void absentFieldPreservesExistingCredential(){service.maintain(1L,2L,null);verifyNoInteractions(jdbc,auth,scopes);}
 @Test void unauthorizedMutationNeverReadsEmail(){assertThatThrownBy(()->service.maintain(1L,2L,new TextNode("a@example.com"))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);verifyNoInteractions(jdbc);}
 @Test void normalizedAdminCredentialRevokesSessions(){permitted();service.maintain(1L,2L,new TextNode(" A@Example.COM "));verify(scopes).requireTarget(1L,10L);verify(jdbc).update("insert into auth_verified_email(user_id,email,verified_at,maintenance_source) values(?,?,current_timestamp,'ADMIN_MAINTAINED')",2L,"a@example.com");verify(auth).revokeAllActiveSessionsForUser(2L);}
 @Test void malformedEmailDoesNotWrite(){permitted();assertThatThrownBy(()->service.maintain(1L,2L,new TextNode("invalid"))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(jdbc,auth);}
 @Test void outOfScopeExistingOrganizationRejectsCredential(){permitted();doThrow(new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("scope")).when(scopes).requireTarget(1L,10L);assertThatThrownBy(()->service.maintain(1L,2L,new TextNode("a@example.com"))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);verifyNoInteractions(jdbc,auth);}
 @Test void creationUsesCreatePermissionWithoutUpdatePermission(){when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_CREATE")).thenReturn(true);when(memberships.findOrganizationIds(2L)).thenReturn(List.of(10L));service.initializeCreatedUser(1L,2L,10L," A@Example.COM ");verify(decisions,never()).isAllowed(1L,PermissionClient.WEB,"IAM_USER_UPDATE");verify(jdbc).update("insert into auth_verified_email(user_id,email,verified_at,maintenance_source) values(?,?,current_timestamp,'ADMIN_MAINTAINED')",2L,"a@example.com");}
 @Test void creationDuplicateEmailThrowsAndDoesNotRevokeExistingAccounts(){when(decisions.isAllowed(1L,PermissionClient.WEB,"IAM_USER_CREATE")).thenReturn(true);when(memberships.findOrganizationIds(2L)).thenReturn(List.of(10L));when(jdbc.update("insert into auth_verified_email(user_id,email,verified_at,maintenance_source) values(?,?,current_timestamp,'ADMIN_MAINTAINED')",2L,"a@example.com")).thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));assertThatThrownBy(()->service.initializeCreatedUser(1L,2L,10L,"a@example.com")).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(auth,audit);}
 @Test void emptyCreationEmailIsOptional(){service.initializeCreatedUser(1L,2L,10L," ");verifyNoInteractions(jdbc,decisions,auth);}

}
