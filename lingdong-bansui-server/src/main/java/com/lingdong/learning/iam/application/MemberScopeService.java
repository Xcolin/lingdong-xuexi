package com.lingdong.learning.iam.application;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class MemberScopeService {
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.permission.application.PermissionDecisionService decisions;
 private final OrganizationMapper organizations;
 private final UserOrganizationMapper memberships;
 private final UserRoleMapper roles;
 private final OrganizationDataScopeService scopes;
 public MemberScopeService(OrganizationMapper organizations, UserOrganizationMapper memberships, UserRoleMapper roles, OrganizationDataScopeService scopes) {
  this.organizations=organizations;this.memberships=memberships;this.roles=roles;this.scopes=scopes;
 }
 public Organization requireTarget(Long operatorId,Long id) {
  Organization org=organizations.findById(id);
  if(org==null)throw new ResourceNotFoundException("组织不存在："+id);
  if(!scopes.resolve(operatorId).allows(org))throw new SystemOperationAccessDeniedException("组织超出实际数据范围");
  return org;
 }
 public Organization school(Organization org) {
  Set<Long> seen=new HashSet<>();
  while(org!=null&&seen.add(org.id())) {if(com.lingdong.learning.organization.application.OrganizationHierarchy.isInstitutionRoot(org))return org;org=org.parentId()==null?null:organizations.findById(org.parentId());}
  return null;
 }
 public boolean canAttachUnbound(Long operatorId) {return scopes.resolve(operatorId).allOrganizations()
  &&(decisions.isAllowed(operatorId,com.lingdong.learning.common.security.RequestPermissionClient.forOperator(operatorId),"ORG_MEMBER_ASSOCIATE")
   ||decisions.isAllowed(operatorId,com.lingdong.learning.common.security.RequestPermissionClient.forOperator(operatorId),"ORG_ADMIN_ASSIGN"));}
 public void requireUser(Long operatorId,Long userId,Organization target) {
  Organization school=school(target);
  var scope=scopes.resolve(operatorId);
  List<Organization> userOrgs=memberships.findOrganizationIds(userId).stream().map(organizations::findById).filter(Objects::nonNull).toList();
  List<Organization> schools=userOrgs.stream().map(this::school).filter(Objects::nonNull).toList();
  if(school!=null) {
   if(!schools.isEmpty()&&schools.stream().noneMatch(s->s.id().equals(school.id())))throw new SystemOperationAccessDeniedException("不能关联仅归属其他学校的用户");
   if(schools.isEmpty()) {if(!canAttachUnbound(operatorId))throw new SystemOperationAccessDeniedException("未归属学校账号需实际全部范围及对应关联或管理员指定权限");}
   else if(userOrgs.stream().noneMatch(scope::allows))throw new SystemOperationAccessDeniedException("用户超出实际数据范围");
  } else if(!scope.allOrganizations()&&userOrgs.stream().noneMatch(scope::allows))throw new SystemOperationAccessDeniedException("用户超出实际数据范围");
 }
 public void requireVisibleUser(Long operatorId,Long userId) {
  var scope=scopes.resolve(operatorId);
  if(!scope.allOrganizations()&&memberships.findOrganizationIds(userId).stream().map(organizations::findById).filter(Objects::nonNull).noneMatch(scope::allows))
   throw new SystemOperationAccessDeniedException("用户超出实际数据范围");
 }
 public boolean all(Long id){return scopes.resolve(id).allOrganizations();}
 public List<Organization> orderedAccessible(Long operatorId,Long filterId) {
  Organization filter=filterId==null?null:requireTarget(operatorId,filterId);
  var scope=scopes.resolve(operatorId);
  List<Organization> all=organizations.findAll();
  Map<Long,List<Organization>> children=new HashMap<>();Set<Long> ids=new HashSet<>();all.forEach(o->ids.add(o.id()));
  all.forEach(o->children.computeIfAbsent(o.parentId()!=null&&ids.contains(o.parentId())?o.parentId():0L,k->new ArrayList<>()).add(o));
  children.values().forEach(list->list.sort(Comparator.comparing(Organization::sortOrder,Comparator.nullsFirst(Integer::compareTo)).thenComparing(Organization::id)));
  List<Organization> ordered=new ArrayList<>();walk(0L,children,ordered,new HashSet<>());
  return ordered.stream().filter(scope::allows).filter(o->filter==null||o.path().startsWith(filter.path())).toList();
 }
 private void walk(Long parent,Map<Long,List<Organization>> children,List<Organization> out,Set<Long> seen) {
  for(Organization child:children.getOrDefault(parent,List.of()))if(seen.add(child.id())){out.add(child);walk(child.id(),children,out,seen);}
 }
}
