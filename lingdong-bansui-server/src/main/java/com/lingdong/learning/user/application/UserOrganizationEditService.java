package com.lingdong.learning.user.application;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.iam.application.MemberScopeService;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class UserOrganizationEditService {
 private final UserAccessApplicationService access; private final UserMapper users; private final UserOrganizationMapper memberships;
 private final OrganizationMapper organizations; private final MemberScopeService scopes; private final PermissionDecisionService decisions; private final UserOrganizationEditMapper guards;
 public UserOrganizationEditService(UserAccessApplicationService a,UserMapper u,UserOrganizationMapper m,OrganizationMapper o,MemberScopeService s,PermissionDecisionService d,UserOrganizationEditMapper g){access=a;users=u;memberships=m;organizations=o;scopes=s;decisions=d;guards=g;}
 public record Option(String id,String parentId,String name,String typeCode,boolean disabled){}
 public record Context(List<String> organizationIds,List<String> lockedOrganizationIds,List<Option> options){}
 private User requireUser(Long actor,Long id){if(!decisions.isAllowed(actor,PermissionClient.WEB,"IAM_USER_UPDATE"))throw new SystemOperationAccessDeniedException("缺少用户编辑权限");scopes.requireVisibleUser(actor,id);User user=users.findById(id);if(user==null)throw new ResourceNotFoundException("用户不存在");return user;}
 public Context context(Long actor,Long id){User user=requireUser(actor,id);List<Long> current=memberships.findOrganizationIds(id);var available=scopes.orderedAccessible(actor,null);Set<Long> visible=new HashSet<>(available.stream().map(Organization::id).toList());Set<Long> locked=new HashSet<>(guards.protectedOrganizationIds(id));current.stream().filter(i->!visible.contains(i)).forEach(locked::add);
 boolean canChange=true;
 List<Option> options=new ArrayList<>();for(Organization org:available){boolean wrong=false;try{OrganizationUserTypePolicy.validate(org.typeCode(),user.type());}catch(IllegalArgumentException e){wrong=true;}options.add(new Option(org.id().toString(),org.parentId()==null?null:org.parentId().toString(),org.name(),org.typeCode(),locked.contains(org.id())||wrong||!canChange||!OrganizationOperationalStatusService.isOperational(org)));}
 for(Long orgId:current)if(!visible.contains(orgId))options.add(new Option(orgId.toString(),null,"已关联组织（超出当前范围）","",true));
 return new Context(current.stream().map(Object::toString).toList(),locked.stream().map(Object::toString).toList(),options);}
 @Transactional public User update(Long actor,Long id,String name,String mobile,List<Long> requested){return update(actor,id,name,mobile,requested,null);}
 @Transactional public User update(Long actor,Long id,String name,String mobile,List<Long> requested,List<Long> expected){requireUser(actor,id);User user=users.findByIdForUpdate(id);if(user==null)throw new ResourceNotFoundException("用户不存在");if(user.status()==UserStatus.CANCELLED)throw new IllegalArgumentException("已注销用户不能编辑");
 if(requested==null||requested.isEmpty()||requested.size()>200||requested.stream().anyMatch(Objects::isNull)||new HashSet<>(requested).size()!=requested.size())throw new IllegalArgumentException("请选择至少一个所属组织，且不可重复");
 Set<Long> before=new HashSet<>(memberships.findOrganizationIds(id));if(expected!=null&&!before.equals(new HashSet<>(expected)))throw new IllegalArgumentException("用户组织关联已变化，请重新打开编辑");Set<Long> after=new HashSet<>(requested);Set<Long> added=new HashSet<>(after);added.removeAll(before);Set<Long> removed=new HashSet<>(before);removed.removeAll(after);
 if(!added.isEmpty()||!removed.isEmpty()){Set<Long> protectedIds=new HashSet<>(guards.protectedOrganizationIds(id));for(Long old:removed){scopes.requireTarget(actor,old);if(protectedIds.contains(old))throw new IllegalArgumentException("所选组织存在管理员、角色或学生教师业务关系，请通过对应业务功能调整");}
 for(Long orgId:added){Organization org=scopes.requireTarget(actor,orgId);OrganizationUserTypePolicy.validate(org.typeCode(),user.type());if(!OrganizationOperationalStatusService.isOperational(org))throw new IllegalArgumentException("不能关联已停用组织");scopes.requireUser(actor,id,org);if(user.type()==com.lingdong.learning.user.domain.UserType.STUDENT&&com.lingdong.learning.organization.application.OrganizationHierarchy.schoolOf(org,organizations)!=null)throw new IllegalArgumentException("学生学校与班级请通过入学申请或学员关系调整");}
 for(Long orgId:added)access.associateWithOrganization(new AssociateUserWithOrganizationCommand(id,orgId,actor));for(Long orgId:removed)access.dissociateFromOrganization(new AssociateUserWithOrganizationCommand(id,orgId,actor));}
 return access.updateProfile(actor,id,name,mobile);}
}
