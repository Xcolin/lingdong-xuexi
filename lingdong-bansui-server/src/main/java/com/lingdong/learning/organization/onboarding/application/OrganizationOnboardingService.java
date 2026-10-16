package com.lingdong.learning.organization.onboarding.application;
import com.lingdong.learning.organization.onboarding.infrastructure.persistence.OnboardingMapper;
import com.lingdong.learning.organization.onboarding.infrastructure.persistence.MobileVerificationMapper;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.audit.application.*;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class OrganizationOnboardingService {
 @org.springframework.beans.factory.annotation.Autowired private MobileVerificationMapper mobileVerification;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.organization.onboarding.infrastructure.persistence.EmailVerificationMapper emailVerification;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.feature.application.FeatureAccessService features;
 private final OnboardingMapper mapper; private final UserMapper users; private final OrganizationMapper organizations;
 private final UserOrganizationMapper memberships; private final UserRoleMapper grants; private final RoleMapper roles;
 private final OrganizationAdminMapper admins; private final ParentAuthenticationMapper profiles; private final IdGenerator ids;private final SystemTaskMapper tasks;
 public OrganizationOnboardingService(OnboardingMapper mapper,UserMapper users,OrganizationMapper organizations,UserOrganizationMapper memberships,UserRoleMapper grants,RoleMapper roles,OrganizationAdminMapper admins,ParentAuthenticationMapper profiles,IdGenerator ids,SystemTaskMapper tasks){this.mapper=mapper;this.users=users;this.organizations=organizations;this.memberships=memberships;this.grants=grants;this.roles=roles;this.admins=admins;this.profiles=profiles;this.ids=ids;this.tasks=tasks;}
 public record Form(String typeCode,String name,Long regionId,String contactName,Integer versionNo,Integer submissionBatch){}
 public void requireOnboardingTask(Long taskId){if(mapper.byTask(taskId)==null)throw new IllegalArgumentException("入驻审核任务不存在或批次已失效");}
 public record MyOrganization(String id,String name,String typeCode,List<String> roleCodes,boolean owner,boolean canEnterFamily,boolean canEnterInstitution){}
 private User actor(Long id){var user=users.findByIdForUpdate(id);if(user==null||user.status()!=UserStatus.ENABLED||user.type()==UserType.STUDENT)throw new IllegalArgumentException("需要有效的非学生账号");return user;}
 private static String required(String text,int max){if(text==null||text.isBlank()||text.trim().length()>max)throw new IllegalArgumentException("必填字段为空或过长");return text.trim();}
 @Transactional public Map<String,Object> context(Long userId){var user=actor(userId);Map<String,Object> result=new HashMap<>();result.put("userId",userId.toString());result.put("mobile",user.mobile());String email=emailVerification==null?null:emailVerification.verifiedEmail(userId);result.put("email",email);result.put("verifiedEmail",email!=null&&!email.isBlank());result.put("verifiedMobile",user.mobile()!=null&&!user.mobile().isBlank()&&mobileVerification.verified(userId,user.mobile()));return result;}
 @Transactional public List<Map<String,Object>> regions(Long userId){actor(userId);return organizations.findAll().stream().filter(o->Set.of("CITY","REGION").contains(o.typeCode())&&o.effectiveStatus()==OrganizationEffectiveStatus.ENABLED).map(o->Map.<String,Object>of("id",o.id().toString(),"name",o.name(),"typeCode",o.typeCode())).toList();}
 @Transactional public List<MyOrganization> myOrganizations(Long userId){actor(userId);var linked=memberships.findOrganizationIds(userId);if(linked.isEmpty())return List.of();return organizations.findByIds(linked).stream().filter(o->o.effectiveStatus()==OrganizationEffectiveStatus.ENABLED).map(o->my(userId,o)).toList();}
 private MyOrganization my(Long userId,Organization o){
  boolean family="FAMILY".equals(o.typeCode());var role=roles.findByCode(family?"PARENT_ADMIN":"ORG_ADMIN");
  boolean admin=admins.exists(userId,o.id())&&role!=null&&role.status()==RoleStatus.ENABLED&&grants.exists(userId,role.id(),"ORG:"+o.id());
  var codes=new LinkedHashSet<>(mapper.roleCodes(userId,o.id()));
  if(family&&grants.hasRoleCode(userId,"PARENT"))codes.add("PARENT");
  if(!family&&grants.hasRoleCode(userId,"ORG_MEMBER"))codes.add("ORG_MEMBER");
  boolean parentMember=family&&memberships.exists(userId,o.id())&&profiles.findProfileByUserId(userId)!=null&&grants.hasAnyEnabledRole(userId);
  return new MyOrganization(o.id().toString(),o.name(),o.typeCode(),List.copyOf(codes),userId.equals(mapper.ownerId(o.id())),family&&(admin||parentMember),!family&&admin);
 }
 @Transactional public MyOrganization family(Long userId,String name,String key,Long regionId){
 actor(userId);if(regionId==null)throw new IllegalArgumentException("请选择所属区域");
 var region=organizations.findByIdForUpdate(regionId);
 if(region==null||!Set.of("CITY","REGION").contains(region.typeCode())||region.effectiveStatus()!=OrganizationEffectiveStatus.ENABLED)throw new IllegalArgumentException("请选择有效所属区域");
 return familyInternal(userId,name,key,region);
 }
 @Transactional public MyOrganization family(Long userId,String name,String key){return familyInternal(userId,name,key,null);}
 private MyOrganization familyInternal(Long userId,String name,String key,Organization region){actor(userId);name=required(name,128);key=required(key,64);Long existing=mapper.family(userId,key);if(existing!=null){var org=organizations.findById(existing);if(!name.equals(org.name()) || (region!=null&&!region.id().equals(org.parentId())))throw new IllegalArgumentException("幂等键已用于其他家庭名称");return my(userId,org);}Organization org=create(userId,"FAMILY",name,region);if(profiles.findProfileByUserId(userId)==null && profiles.insertProfile(new com.lingdong.learning.auth.application.ParentProfile(ids.nextId(),userId,com.lingdong.learning.auth.application.ParentOnboardingStatus.PENDING,java.time.LocalDateTime.now(),null))!=1)throw new IllegalStateException("家庭身份档案保存失败");mapper.familyRequest(userId,key,org.id(),name);return my(userId,org);}
 private Organization create(Long userId,String type,String name,Organization region){features.requireEnabled("ORGANIZATION_MANAGEMENT",null);long id=ids.nextId();String code="ONB"+id;String path=region==null?"/"+code+"/":region.path()+code+"/";var org=Organization.create(id,region==null?null:region.id(),region==null?"FAMILY:"+id:"PARENT:"+region.id(),code,name,type,path,0,null);var role=roles.findByCode("FAMILY".equals(type)?"PARENT_ADMIN":"ORG_ADMIN");if(role==null||role.status()!=RoleStatus.ENABLED)throw new IllegalStateException("管理员角色不可用");if(organizations.insert(org)!=1||memberships.insert(ids.nextId(),userId,id)!=1||grants.insert(ids.nextId(),userId,role.id(),id,"ORG:"+id)!=1||grants.markAutomaticOrganizationAdmin(userId,role.id(),id)!=1||admins.insert(ids.nextId(),userId,id)!=1||mapper.owner(id,userId)!=1)throw new IllegalStateException("组织及管理员授权保存失败");return org;}
 private Map<String,Object> form(Long userId,Form form){features.requireEnabled("ORGANIZATION_MANAGEMENT",null);var user=actor(userId);if(!Set.of("SCHOOL","TRAINING_INSTITUTION").contains(form.typeCode()))throw new IllegalArgumentException("机构类型无效");var region=organizations.findByIdForUpdate(form.regionId());if(region==null||!Set.of("CITY","REGION").contains(region.typeCode())||region.effectiveStatus()!=OrganizationEffectiveStatus.ENABLED)throw new IllegalArgumentException("请选择有效区域");String email=emailVerification==null?null:emailVerification.verifiedEmail(userId);boolean verifiedMobile=user.mobile()!=null&&!user.mobile().isBlank()&&mobileVerification.verified(userId,user.mobile());if((email==null||email.isBlank())&&!verifiedMobile)throw new IllegalArgumentException("请先验证邮箱或手机号");Map<String,Object> row=new HashMap<>();row.put("userId",userId);row.put("typeCode",form.typeCode());row.put("name",required(form.name(),128));row.put("normalizedName",required(form.name(),128).toLowerCase(Locale.ROOT));row.put("regionId",form.regionId());row.put("contactName",required(form.contactName(),64));row.put("mobile",verifiedMobile?user.mobile():null);row.put("email",email);if(mapper.existing(row)>0)throw new IllegalArgumentException("同区域已有同名机构，请联系平台");return row;}
 @Transactional public Map<String,Object> submit(Long userId,Form form){var row=form(userId,form);long id=ids.nextId();row.put("id",id);row.put("taskId",task(userId,row,1));mapper.insert(row);mapper.reserve(row);mapper.event(ids.nextId(),id,1,"SUBMIT",userId,null);return response(mapper.lock(id));}
 private Long task(Long userId,Map<String,Object> row,int batch){long taskId=ids.nextId();tasks.insert(new SystemTask(taskId,UUID.randomUUID().toString(),SystemTaskType.ORGANIZATION_ONBOARDING,"机构入驻："+row.get("name"),"类型："+row.get("typeCode")+"，联系人："+row.get("contactName")+"，申请批次："+batch,ImpactScope.REGION,SystemTaskStatus.DRAFT,userId,null,null,null,null,null,null));if(tasks.updateSubmission(taskId,SystemTaskStatus.PENDING_REVIEW)!=1)throw new IllegalStateException("审核任务提交失败");return taskId;}
 private Map<String,Object> owned(Long userId,Long id){actor(userId);var row=mapper.lock(id);if(row==null||!userId.equals(((Number)row.get("applicantId")).longValue()))throw new IllegalArgumentException("申请不存在");return row;}
 private void version(Map<String,Object> row,Integer version,Integer batch,String...states){if(version==null||batch==null||((Number)row.get("versionNo")).intValue()!=version||((Number)row.get("submissionBatch")).intValue()!=batch||!Arrays.asList(states).contains(row.get("status")))throw new IllegalStateException("申请状态或批次已更新，请刷新");}
 @Transactional public List<Map<String,Object>> applications(Long userId){actor(userId);return mapper.ids(userId).stream().map(mapper::lock).map(this::response).toList();}
 @Transactional public Map<String,Object> application(Long userId,Long id){return response(owned(userId,id));}
 @Transactional public Map<String,Object> withdraw(Long userId,Long id,Integer v,Integer b){var row=owned(userId,id);version(row,v,b,"PENDING");row.put("status","WITHDRAWN");row.put("organizationId",null);row.put("reason",null);if(mapper.transition(row)!=1)throw new IllegalStateException("申请已更新");mapper.release(id);if(tasks.updateReview(((Number)row.get("taskId")).longValue(),SystemTaskStatus.REJECTED,null,"申请人已撤回本批次入驻申请")!=1)throw new IllegalStateException("审核任务已更新，请刷新");mapper.event(ids.nextId(),id,b,"WITHDRAW",userId,null);return response(mapper.lock(id));}
 @Transactional public Map<String,Object> resubmit(Long userId,Long id,Form form){var old=owned(userId,id);version(old,form.versionNo(),form.submissionBatch(),"REJECTED","WITHDRAWN");var row=form(userId,form);row.put("id",id);row.put("versionNo",form.versionNo());row.put("submissionBatch",form.submissionBatch());row.put("taskId",task(userId,row,form.submissionBatch()+1));if(mapper.resubmit(row)!=1)throw new IllegalStateException("申请已更新");mapper.reserve(row);mapper.event(ids.nextId(),id,form.submissionBatch()+1,"SUBMIT",userId,null);return response(mapper.lock(id));}
 @Transactional public void review(Long taskId,Long reviewer,boolean approved,String reason){Long id=mapper.byTask(taskId);if(id==null)throw new IllegalStateException("入驻申请不存在");var row=mapper.lock(id);if(!"PENDING".equals(row.get("status"))||!taskId.equals(((Number)row.get("taskId")).longValue()))throw new IllegalStateException("该申请批次已失效");Long applicant=((Number)row.get("applicantId")).longValue();if(applicant.equals(reviewer))throw new IllegalStateException("不能审核本人申请");actor(applicant);Long organizationId=null;if(approved){if(mapper.existing(row)>0)throw new IllegalStateException("同区域已有同名机构");var region=organizations.findByIdForUpdate(((Number)row.get("regionId")).longValue());if(region==null||region.effectiveStatus()!=OrganizationEffectiveStatus.ENABLED)throw new IllegalStateException("区域已停用");organizationId=create(applicant,row.get("typeCode").toString(),row.get("name").toString(),region).id();}else required(reason,1000);row.put("status",approved?"APPROVED":"REJECTED");row.put("organizationId",organizationId);row.put("reason",reason);if(mapper.transition(row)!=1)throw new IllegalStateException("申请已更新");if(!approved)mapper.release(id);mapper.event(ids.nextId(),id,((Number)row.get("submissionBatch")).intValue(),approved?"APPROVE":"REJECT",reviewer,reason);}
 private Map<String,Object> response(Map<String,Object> source){Map<String,Object> out=new HashMap<>(source);out.remove("applicantId");out.remove("normalizedName");for(String key:List.of("id","regionId","taskId","organizationId"))if(out.get(key)!=null)out.put(key,out.get(key).toString());return out;}
}







