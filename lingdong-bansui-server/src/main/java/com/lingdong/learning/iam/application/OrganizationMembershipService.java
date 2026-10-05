package com.lingdong.learning.iam.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.DataScopeAdministrationService;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.HashSet;

/** Maintains direct membership and scoped administrator grants atomically. */
@Service
public class OrganizationMembershipService {
 private final UserMapper users;
 private final OrganizationMapper organizations;
 private final UserRoleMapper userRoles;
 private final UserOrganizationMapper memberships;
 private final OrganizationAdminMapper administrators;
 private final RoleMapper roles;
 private final UserAccessApplicationService access;
 private final DataScopeAdministrationService scopes;
 private final PermissionDecisionService decisions;
 public OrganizationMembershipService(UserMapper users, OrganizationMapper organizations, UserRoleMapper userRoles,
  UserOrganizationMapper memberships, OrganizationAdminMapper administrators, RoleMapper roles,
  UserAccessApplicationService access, DataScopeAdministrationService scopes, PermissionDecisionService decisions) {
  this.users=users;this.organizations=organizations;this.userRoles=userRoles;this.memberships=memberships;
  this.administrators=administrators;this.roles=roles;this.access=access;this.scopes=scopes;this.decisions=decisions;
 }
 public record Member(User user, boolean administrator) { }
 public record MemberPage(List<Member> items,int page,int pageSize,long total) { }
 public record MemberRelation(
  @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long userId,
  boolean administrator) { }
 @Transactional(readOnly=true)
 public List<MemberRelation> memberRelations(Long operatorId, Long organizationId, List<Long> userIds) {
  requireAdministrator(operatorId,false);requirePermission(operatorId,"IAM_USER_LIST");
  if(userIds==null||userIds.isEmpty()||userIds.size()>100||userIds.stream().anyMatch(java.util.Objects::isNull)
    ||new HashSet<>(userIds).size()!=userIds.size())throw new IllegalArgumentException("候选用户集合无效，最多100人且不得重复或包含空项");
  if(organizations.findById(organizationId)==null)throw new ResourceNotFoundException("组织不存在："+organizationId);
  return userIds.stream().filter(id->memberships.exists(id,organizationId))
   .map(id->new MemberRelation(id,administrators.exists(id,organizationId))).toList();
 }
 public List<RoleUserAssignment> roleUsers(Long operatorId,Long roleId) {
  requirePermission(operatorId,"IAM_USER_ROLE_ASSIGN");
  if(roles.findById(roleId)==null)throw new ResourceNotFoundException("角色不存在："+roleId);
  return userRoles.findByRoleId(roleId);
 }
 @Transactional(readOnly=true)
 public MemberPage members(Long operatorId,Long organizationId,String keyword,int page,int pageSize) {
  requireAdministrator(operatorId,false); requirePermission(operatorId,"IAM_USER_LIST");
  if(organizations.findById(organizationId)==null)throw new ResourceNotFoundException("组织不存在："+organizationId);
  if(page<1||pageSize<1||pageSize>200 || (long)(page-1)*pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("分页参数无效");
  String query=keyword==null||keyword.isBlank()?null:keyword.trim();
  if(query!=null&&query.length()>128)throw new IllegalArgumentException("关键词过长");
  List<Member> items=users.findOrganizationMembers(organizationId,query,(page-1)*pageSize,pageSize).stream()
   .map(user->new Member(user,administrators.exists(user.id(),organizationId))).toList();
  return new MemberPage(items,page,pageSize,users.countOrganizationMembers(organizationId,query));
 }
 @Transactional
 public void addMembers(Long operatorId,Long organizationId,List<Long> userIds,boolean administrator) {
  requireAdministrator(operatorId,true);requirePermission(operatorId,"IAM_USER_ORGANIZATION_ASSIGN");
  if(administrator){requirePermission(operatorId,"IAM_DATA_SCOPE_CONFIGURE");requirePermission(operatorId,"IAM_USER_ROLE_ASSIGN");}
  if(userIds==null||userIds.isEmpty()||userIds.size()>200||userIds.contains(null)||new HashSet<>(userIds).size()!=userIds.size())
   throw new IllegalArgumentException("用户集合无效，最多200人且不得重复或包含空项");
  Organization org=organizations.findByIdForUpdate(organizationId);
  if(org==null)throw new ResourceNotFoundException("组织不存在："+organizationId);
  if(!OrganizationOperationalStatusService.isOperational(org))throw new IllegalStateException("组织已停用，不能关联成员");
  Role role=administrator?roles.findByCode("ORG_ADMIN"):null;
  if(administrator&&(role==null||role.status()!=RoleStatus.ENABLED))throw new IllegalStateException("组织管理员角色不可用");
  List<Long> ordered=userIds.stream().sorted().toList();
  // Stable locking order and all-user preflight prevent partial batches and concurrent duplicates.
  for(Long id:ordered){User user=users.findByIdForUpdate(id);if(user==null)throw new ResourceNotFoundException("用户不存在："+id);
   if(user.status()!=UserStatus.ENABLED)throw new IllegalStateException("用户已停用，不能关联组织："+id);}
  for(Long id:ordered){
   if(memberships.lockRelation(id,organizationId)==null)access.associateWithOrganization(new AssociateUserWithOrganizationCommand(id,organizationId,operatorId));
   if(administrator){
    if(userRoles.lockRelation(id,role.id(),"ORG:"+organizationId)==null)access.assignRole(new AssignRoleToUserCommand(id,role.id(),organizationId,operatorId));
    if(administrators.lockRelation(id,organizationId)==null)scopes.configureOrganizationAdministrator(operatorId,id,organizationId);
   }
  }
 }
 private void requireAdministrator(Long id,boolean write){
  User operator=id==null?null:users.findById(id);
  if(operator==null||operator.status()!=UserStatus.ENABLED)
   throw new SystemOperationAccessDeniedException("仅启用中的系统管理员可维护组织成员，审核员只读");
 }
 private void requirePermission(Long id,String code){if(!decisions.isAllowed(id,PermissionClient.WEB,code))throw new SystemOperationAccessDeniedException("缺少操作权限："+code);}
}

