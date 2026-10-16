package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@RestController @RequestMapping("/api/v1/account-management")
public class AccountManagementController {
 private final UserMapper users;private final UserOrganizationMapper memberships;private final PermissionDecisionService permissions;private final OrganizationDataScopeService scope;private final AuthenticationApplicationService auth;private final InitialPasswordStateService initial;
 public AccountManagementController(UserMapper users,UserOrganizationMapper memberships,PermissionDecisionService permissions,OrganizationDataScopeService scope,AuthenticationApplicationService auth,InitialPasswordStateService initial){this.users=users;this.memberships=memberships;this.permissions=permissions;this.scope=scope;this.auth=auth;this.initial=initial;}
 public record UserView(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long id,String username,String displayName,UserType userType){}
 public record Reset(Long organizationId,String password){}
 private void require(AuthenticatedUser actor,Long org){if(!permissions.isAllowed(actor.userId(),PermissionClient.valueOf(actor.clientType().name()),"IAM_USER_PASSWORD_SET")||!scope.canAccess(actor.userId(),org))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("无账号密码管理权限");var selected=com.lingdong.learning.common.security.SelectedOrganizationContext.organization();if(selected==null||!selected.id().equals(org))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("请选择当前组织");}
 @GetMapping("/users") public List<UserView> list(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam Long organizationId,@RequestParam(defaultValue="") String keyword){require(actor,organizationId);return users.findOrganizationMembers(organizationId,keyword,0,100).stream().filter(u->u.type()!=UserType.STUDENT&&u.type()!=UserType.PLATFORM).map(u->new UserView(u.id(),u.username(),u.displayName(),u.type())).toList();}
 @Transactional @PostMapping("/users/{id}/password-reset") public void reset(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@RequestBody Reset c){require(actor,c.organizationId());var user=users.findByIdForUpdate(id);if(user==null||user.type()==UserType.STUDENT||user.type()==UserType.PLATFORM||user.status()==UserStatus.CANCELLED||!memberships.findOrganizationIds(id).contains(c.organizationId()))throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("目标账号不属于当前组织");String hash=auth.encodeManagedUserPassword(c.password());if(users.updatePasswordHash(id,hash)!=1)throw new IllegalStateException("密码保存失败");initial.require(id);auth.revokeAllActiveSessionsForUser(id);}
}

