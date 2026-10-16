package com.lingdong.learning.user.application;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class UserRoleAssociationService {
 @org.springframework.beans.factory.annotation.Autowired private RoleIdentityCompatibilityService roleIdentityCompatibility;
 private final UserAccessApplicationService access;private final UserMapper users;private final RoleMapper roles;private final UserRoleAssociationMapper relations;private final MemberScopeService scopes;private final PermissionDecisionService decisions;
 public UserRoleAssociationService(UserAccessApplicationService a,UserMapper u,RoleMapper r,UserRoleAssociationMapper m,MemberScopeService s,PermissionDecisionService d){access=a;users=u;roles=r;relations=m;scopes=s;decisions=d;}
 public record RoleOption(String id,String code,String name,String dataScope,String status){}
 public record Assignment(String roleId,String organizationId,String organizationName,boolean removable){}
 public record OrganizationOption(String id,String parentId,String name,String typeCode,boolean disabled){}
 public record Context(List<RoleOption> roles,List<Assignment> assignments,List<OrganizationOption> organizations,boolean globalAssignable){}
 private void requireAccess(Long actor,Long user){if(!decisions.isAllowed(actor,PermissionClient.WEB,"IAM_USER_ROLE_ASSIGN"))throw new SystemOperationAccessDeniedException("缺少用户角色关联权限");if(users.findById(user)==null)throw new ResourceNotFoundException("用户不存在");scopes.requireVisibleUser(actor,user);}
 public Context context(Long actor,Long user){requireAccess(actor,user);var options=scopes.orderedAccessible(actor,null);Map<Long,Organization> visible=new HashMap<>();options.forEach(o->visible.put(o.id(),o));boolean global=scopes.all(actor);
 var catalog=roles.findAll().stream().map(r->new RoleOption(r.id().toString(),r.code(),r.name(),r.dataScope().name(),r.status().name())).toList();
 var assignments=relations.findByUserId(user).stream().filter(r->r.organizationId()==null?(global||catalog.stream().anyMatch(role->role.id().equals(r.roleId().toString())&&role.dataScope().equals("SELF"))):visible.containsKey(r.organizationId())).map(r->new Assignment(r.roleId().toString(),r.organizationId()==null?null:r.organizationId().toString(),r.organizationId()==null?"全部范围":visible.containsKey(r.organizationId())?visible.get(r.organizationId()).name():"超出当前范围",r.organizationId()==null?(global||catalog.stream().anyMatch(role->role.id().equals(r.roleId().toString())&&role.dataScope().equals("SELF"))):visible.containsKey(r.organizationId()))).toList();
 return new Context(catalog,assignments,options.stream().map(o->new OrganizationOption(o.id().toString(),o.parentId()==null?null:o.parentId().toString(),o.name(),o.typeCode(),!com.lingdong.learning.organization.application.OrganizationOperationalStatusService.isOperational(o))).toList(),global);}
 public record Item(Long roleId,Long organizationId){}
 @Transactional public void change(Long actor,Long user,List<Item> items,boolean remove){requireAccess(actor,user);if(users.findByIdForUpdate(user)==null)throw new ResourceNotFoundException("用户不存在");if(items==null||items.isEmpty()||items.size()>200)throw new IllegalArgumentException("请选择需要操作的角色");Set<String> keys=new HashSet<>();for(Item item:items){if(item==null||item.roleId()==null||!keys.add(item.roleId()+":"+item.organizationId()))throw new IllegalArgumentException("角色关联项无效或重复");}
 if(!remove)items.stream().map(Item::roleId).distinct().sorted().forEach(roleId->roleIdentityCompatibility.requireCompatible(user,roleId));
 for(Item item:items){var command=new BatchAssignRoleToRoleCommand(actor,item.roleId(),List.of(new BatchRoleAssignmentItem(user,item.organizationId())));if(remove)access.batchRemoveRole(command);else access.batchAssignRole(command);}}
}
