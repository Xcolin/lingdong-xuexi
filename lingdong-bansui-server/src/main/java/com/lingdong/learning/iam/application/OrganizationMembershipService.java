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
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper parentProfiles;
 @org.springframework.beans.factory.annotation.Autowired private MemberScopeService memberScopes;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.organization.application.OrganizationNamePathService namePaths;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.organization.onboarding.application.OrganizationOwnerMapper owners;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.organization.onboarding.application.OrganizationOwnerGuard ownerGuard;
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
 static boolean familyAdministratorEligible(UserType type,boolean parentProfile){return type!=UserType.STUDENT && (type==UserType.FAMILY || parentProfile);}
 public record Member(User user, boolean administrator, boolean owner) { }
 public record PickerMember(User user,boolean administrator,List<String> organizationNames,List<RoleUserAssignment> assignments) { }
 public record PickerPage(List<PickerMember> items,int page,int pageSize,long total,boolean allowedUnbound) { }
 @Transactional(readOnly=true)
 public PickerPage picker(Long operatorId,Long organizationId,Long roleId,Long grantOrganizationId,boolean administrator,
  String side,Long filterId,String group,String keyword,int page,int pageSize) {
  requireAdministrator(operatorId,false);requirePermission(operatorId,roleId==null?(administrator?"ORG_ADMIN_ASSIGN":"ORG_MEMBER_ASSOCIATE"):"IAM_USER_ROLE_ASSIGN");
  if(!"LINKED".equals(side)&&!"CANDIDATES".equals(side))throw new IllegalArgumentException("列表方向无效");
  if(page<1||pageSize<1||pageSize>100||(long)(page-1)*pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("分页参数无效");
  String search=keyword==null||keyword.isBlank()?null:keyword.trim();if(search!=null&&search.length()>128)throw new IllegalArgumentException("关键词过长");
  Organization target=organizationId==null?null:memberScopes.requireTarget(operatorId,organizationId);
  Organization school=target==null?null:memberScopes.school(target);
  boolean unbound="UNBOUND".equals(group);
  if(group!=null&&!"SCHOOL".equals(group)&&!unbound)throw new IllegalArgumentException("账号分组无效");
  if(organizationId!=null&&unbound)throw new SystemOperationAccessDeniedException("组织用户只能选择本校已归属用户");
  if(unbound&&!memberScopes.canAttachUnbound(operatorId))throw new SystemOperationAccessDeniedException("关联未归属学校账号需要全范围及成员和范围管理权限");
  boolean selfRole=false;
  if(roleId!=null){Role role=roles.findById(roleId);if(role==null)throw new ResourceNotFoundException("角色不存在");
   selfRole=role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.SELF;
   if(grantOrganizationId!=null||!"LINKED".equals(side))access.validateRoleGrant(operatorId,roleId,grantOrganizationId);
   if(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.ALL&&!memberScopes.all(operatorId))throw new SystemOperationAccessDeniedException("全局角色需要全部组织范围");}
  var ordered=memberScopes.orderedAccessible(operatorId,filterId==null&&target!=null&&school==null?target.id():filterId);
  if(school!=null)ordered=ordered.stream().filter(o->o.path().startsWith(school.path())).toList();
  var query=new MemberPickerQuery(organizationId,roleId,grantOrganizationId,administrator,"LINKED".equals(side),unbound,selfRole,
   memberScopes.all(operatorId)&&filterId==null,ordered.stream().map(Organization::id).toList(),school==null?null:school.id(),school==null?null:school.path(),grantOrganizationId==null?null:memberScopes.requireTarget(operatorId,grantOrganizationId).path(),search,(page-1)*pageSize,pageSize);
  var paths=namePaths.namePathsById();
  if(roleId!=null&&query.assigned()) {
   var items=users.findRolePickerAssignments(query).stream().map(a->{User user=users.findById(a.userId());return new PickerMember(user,false,
    pickerOrganizationNames(user.id(),query.organizationIds(),paths),List.of(a));}).toList();
   return new PickerPage(items,page,pageSize,users.countRolePickerAssignments(query),false);
  }
  List<RoleUserAssignment> bindings=List.of();
  var items=users.findPickerPage(query).stream().map(u->new PickerMember(u,organizationId!=null&&administrators.exists(u.id(),organizationId),
   pickerOrganizationNames(u.id(),query.organizationIds(),paths),
   bindings.stream().filter(a->a.userId().equals(u.id())).toList())).toList();
  return new PickerPage(items,page,pageSize,users.countPicker(query),false);
 }
 private List<String> pickerOrganizationNames(Long userId,List<Long> ordered,java.util.Map<Long,String> paths) {
  java.util.Set<Long> ids=new java.util.HashSet<>(memberships.findOrganizationIds(userId));
  return ordered.stream().filter(ids::contains).map(paths::get).filter(java.util.Objects::nonNull).toList();
 }

 public record MemberPage(List<Member> items,int page,int pageSize,long total) { }
 public record MemberRelation(
  @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long userId,
  boolean administrator) { }
 @Transactional(readOnly=true)
 public List<MemberRelation> memberRelations(Long operatorId, Long organizationId, List<Long> userIds) {
  return memberRelations(operatorId,organizationId,userIds,false);
 }
 @Transactional(readOnly=true)
 public List<MemberRelation> memberRelations(Long operatorId, Long organizationId, List<Long> userIds,boolean administrator) {
  requireAdministrator(operatorId,false);requirePermission(operatorId,administrator?"ORG_ADMIN_ASSIGN":"ORG_MEMBER_ASSOCIATE");
  if(userIds==null||userIds.isEmpty()||userIds.size()>100||userIds.stream().anyMatch(java.util.Objects::isNull)
    ||new HashSet<>(userIds).size()!=userIds.size())throw new IllegalArgumentException("候选用户集合无效，最多100人且不得重复或包含空项");
  memberScopes.requireTarget(operatorId,organizationId);
  return userIds.stream().filter(id->memberships.exists(id,organizationId))
   .map(id->new MemberRelation(id,administrators.exists(id,organizationId))).toList();
 }
 public List<RoleUserAssignment> roleUsers(Long operatorId,Long roleId) {
  requirePermission(operatorId,"IAM_USER_ROLE_ASSIGN");
  Role role=roles.findById(roleId);
  if(role==null)throw new ResourceNotFoundException("角色不存在："+roleId);
  boolean all=memberScopes.all(operatorId);
  java.util.Set<Long> visible=memberScopes.orderedAccessible(operatorId,null).stream().map(Organization::id).collect(java.util.stream.Collectors.toSet());
  return userRoles.findByRoleId(roleId).stream().filter(a->a.organizationId()==null?
   all||(role.dataScope()==com.lingdong.learning.iam.domain.RoleDataScope.SELF&&memberships.findOrganizationIds(a.userId()).stream().anyMatch(visible::contains)):
   visible.contains(a.organizationId())).toList();
 }
 @Transactional(readOnly=true)
 public MemberPage members(Long operatorId,Long organizationId,String keyword,int page,int pageSize) {
  requireAdministrator(operatorId,false);
  requirePermission(operatorId,"ORG_MEMBER_READ");
  memberScopes.requireTarget(operatorId,organizationId);
  if(page<1||pageSize<1||pageSize>200 || (long)(page-1)*pageSize>Integer.MAX_VALUE)throw new IllegalArgumentException("分页参数无效");
  String query=keyword==null||keyword.isBlank()?null:keyword.trim();
  if(query!=null&&query.length()>128)throw new IllegalArgumentException("关键词过长");
  Long ownerId=owners==null?null:owners.findOwner(organizationId);
  List<Member> items=users.findOrganizationMembers(organizationId,query,(page-1)*pageSize,pageSize).stream()
   .map(user->new Member(user,administrators.exists(user.id(),organizationId),user.id().equals(ownerId))).toList();
  return new MemberPage(items,page,pageSize,users.countOrganizationMembers(organizationId,query));
 }
 @Transactional
 public void addMembers(Long operatorId,Long organizationId,List<Long> userIds,boolean administrator) {
  requireAdministrator(operatorId,true);requirePermission(operatorId,administrator?"ORG_ADMIN_ASSIGN":"ORG_MEMBER_ASSOCIATE");
  if(userIds==null||userIds.isEmpty()||userIds.size()>200||userIds.stream().anyMatch(java.util.Objects::isNull)||new HashSet<>(userIds).size()!=userIds.size())
   throw new IllegalArgumentException("用户集合无效，最多200人且不得重复或包含空项");
  Organization org=organizations.findByIdForUpdate(organizationId);
  if(org==null)throw new ResourceNotFoundException("组织不存在："+organizationId);
  if(!OrganizationOperationalStatusService.isOperational(org))throw new IllegalStateException("组织已停用，不能关联成员");
  memberScopes.requireTarget(operatorId,organizationId);
  Organization school=memberScopes.school(org);
  Long roleOrganizationId=school==null?organizationId:school.id();
  Role role=administrator?roles.findByCode("FAMILY".equals(org.typeCode())?"PARENT_ADMIN":"ORG_ADMIN"):null;
  if(administrator&&(role==null||role.status()!=RoleStatus.ENABLED))throw new IllegalStateException("组织管理员角色不可用");
  List<Long> ordered=userIds.stream().sorted().toList();
  // Stable locking order and all-user preflight prevent partial batches and concurrent duplicates.
  for(Long id:ordered){User user=users.findByIdForUpdate(id);if(user==null)throw new ResourceNotFoundException("用户不存在："+id);
   if(administrator&&"FAMILY".equals(org.typeCode())&&!familyAdministratorEligible(user.type(),parentProfiles==null || user.type()==UserType.FAMILY || parentProfiles.findProfileByUserId(id)!=null))throw new IllegalArgumentException("家庭管理员必须是家长账号");
   if(user.status()!=UserStatus.ENABLED)throw new IllegalStateException("用户已停用，不能关联组织："+id);memberScopes.requireUser(operatorId,id,org);}
  for(Long id:ordered){
   if(memberships.lockRelation(id,organizationId)==null){
    access.associateWithOrganization(new AssociateUserWithOrganizationCommand(id,organizationId,operatorId));
   }
   if(administrator){
    if(memberships.lockRelation(id,roleOrganizationId)==null)access.associateIdentityOrganization(new AssociateUserWithOrganizationCommand(id,roleOrganizationId,operatorId));
    if(userRoles.lockRelation(id,role.id(),"ORG:"+roleOrganizationId)==null) {
     if(school!=null)access.assignSchoolIdentityRole(new AssignRoleToUserCommand(id,role.id(),roleOrganizationId,operatorId));
     else access.assignRole(new AssignRoleToUserCommand(id,role.id(),roleOrganizationId,operatorId));
     userRoles.markAutomaticOrganizationAdmin(id,role.id(),roleOrganizationId);
    }
    if(administrators.lockRelation(id,organizationId)==null)scopes.configureScopedOrganizationAdministrator(operatorId,id,organizationId);
   }
  }
 }
 @Transactional
 public void revokeMembers(Long operatorId,Long organizationId,List<Long> userIds,boolean administrator) {
  requireAdministrator(operatorId,true);requirePermission(operatorId,administrator?"ORG_ADMIN_REVOKE":"ORG_MEMBER_REMOVE");
  if(userIds==null||userIds.isEmpty()||userIds.size()>200||userIds.stream().anyMatch(java.util.Objects::isNull)||new HashSet<>(userIds).size()!=userIds.size())
   throw new IllegalArgumentException("用户集合无效，最多200人且不得重复或包含空项");
  Organization org=organizations.findByIdForUpdate(organizationId);
  if(org==null)throw new ResourceNotFoundException("组织不存在："+organizationId);
  memberScopes.requireTarget(operatorId,organizationId);
  Organization school=memberScopes.school(org);
  Long roleOrganizationId=school==null?organizationId:school.id();
  Role role=administrator?roles.findByCode("FAMILY".equals(org.typeCode())?"PARENT_ADMIN":"ORG_ADMIN"):null;
  if(administrator&&role==null)throw new IllegalStateException("组织管理员角色不可用");
  List<Long> ordered=userIds.stream().sorted().toList();
  for(Long id:ordered){User user=users.findByIdForUpdate(id);if(user==null)throw new ResourceNotFoundException("用户不存在："+id);
   if(ownerGuard!=null)ownerGuard.requireRemovable(id,organizationId);
   if(!memberships.exists(id,organizationId)&&!administrators.exists(id,organizationId))throw new IllegalStateException("用户未关联所选组织");}
  for(Long id:ordered){
   if(administrator){
    if(administrators.lockRelation(id,organizationId)==null)throw new IllegalStateException("用户不是该组织管理员："+id);
    scopes.revokeScopedOrganizationAdministrator(operatorId,id,organizationId);
    boolean stillManaged=administrators.findOrganizationIds(id).stream().map(organizations::findById).filter(java.util.Objects::nonNull)
     .anyMatch(managed->{Organization managedSchool=memberScopes.school(managed);return roleOrganizationId.equals(managedSchool==null?managed.id():managedSchool.id());});
    if(!stillManaged&&userRoles.isAutomaticOrganizationAdmin(id,role.id(),roleOrganizationId)&&userRoles.lockRelation(id,role.id(),"ORG:"+roleOrganizationId)!=null) {
     if(school!=null)access.removeSchoolIdentityRole(new AssignRoleToUserCommand(id,role.id(),roleOrganizationId,operatorId));
     else access.removeAutomaticOrganizationAdminRole(new AssignRoleToUserCommand(id,role.id(),roleOrganizationId,operatorId));
    }
    if(!roleOrganizationId.equals(organizationId)&&userRoles.lockRelation(id,role.id(),"ORG:"+organizationId)!=null)access.removeAutomaticOrganizationAdminRole(new AssignRoleToUserCommand(id,role.id(),organizationId,operatorId));
   } else {
    if(administrators.exists(id,organizationId))throw new IllegalStateException("用户是组织管理员，请先取消其管理员身份："+id);
    if(memberships.lockRelation(id,organizationId)!=null)access.dissociateFromOrganization(new AssociateUserWithOrganizationCommand(id,organizationId,operatorId));
   }
  }
 }
 private void requireAdministrator(Long id,boolean write){
  User operator=id==null?null:users.findById(id);
  if(operator==null||operator.status()!=UserStatus.ENABLED)
   throw new SystemOperationAccessDeniedException("需要启用的操作账号");
 }
 private void requirePermission(Long id,String code){if(!decisions.isAllowed(id,com.lingdong.learning.common.security.RequestPermissionClient.forOperator(id),code))throw new SystemOperationAccessDeniedException("缺少操作权限："+code);}
}


