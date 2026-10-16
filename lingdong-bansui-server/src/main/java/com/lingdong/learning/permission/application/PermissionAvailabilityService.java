package com.lingdong.learning.permission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.permission.domain.*;
import com.lingdong.learning.menu.infrastructure.persistence.MenuMapper;
import com.lingdong.learning.user.infrastructure.persistence.*;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.util.*;

/** Availability is shared by grants, effective permissions and API authorization. */
@Service
public class PermissionAvailabilityService {
 public record Rule(String code,List<String> features,List<String> allowedUserTypes,List<String> scopeKinds,List<String> impliedReads,List<String> allowedRoleCodes,List<String> optionalReads,List<String> actorPrerequisites,Map<String,List<String>> conditionalReads,List<String> miniappImpliedReads) {}
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper permissions;
 @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.teacher.infrastructure.persistence.TeacherProfileMapper teacherProfiles;
 private final Map<String,Rule> rules=new HashMap<>();
 private final FeatureAccessService features; private final MenuMapper menus; private final UserMapper users;
 private final ParentAuthenticationMapper parents; private final UserRoleMapper roles; private final RoleMapper roleCatalog;
 public PermissionAvailabilityService(ObjectMapper json,FeatureAccessService features,MenuMapper menus,UserMapper users,ParentAuthenticationMapper parents,UserRoleMapper roles,RoleMapper roleCatalog) throws java.io.IOException {
  this.features=features;this.menus=menus;this.users=users;this.parents=parents;this.roles=roles;this.roleCatalog=roleCatalog;
  try(var in=new ClassPathResource("authorization-model.json").getInputStream()) {
   for(var node:json.readTree(in).path("policies")) rules.put(node.path("code").asText(),new Rule(node.path("code").asText(),strings(node.path("features")),strings(node.path("allowedUserTypes")),strings(node.path("scopeKinds")),strings(node.path("impliedReads")),strings(node.path("allowedRoleCodes")),strings(node.path("optionalReads")),strings(node.path("actorPrerequisites")),readMap(node.path("conditionalReads")),strings(node.path("miniappImpliedReads"))));
  }
 }
 private static List<String> strings(com.fasterxml.jackson.databind.JsonNode node){List<String> out=new ArrayList<>();node.forEach(n->out.add(n.asText()));return List.copyOf(out);}
 private static Map<String,List<String>> readMap(com.fasterxml.jackson.databind.JsonNode node){Map<String,List<String>> result=new HashMap<>();node.fields().forEachRemaining(e->result.put(e.getKey(),strings(e.getValue())));return Map.copyOf(result);}
 public Set<String> impliedReads(String code,Long userId){
  Set<String> result=impliedReads(code);Rule rule=rules.get(code);var account=AuthorizationRequestCache.get("user:"+userId,()->users.findById(userId));
  if(rule!=null && account!=null)result.addAll(rule.conditionalReads().getOrDefault(account.type().name(),List.of()));
  return result;
 }
 private boolean featureEnabled(String code){return AuthorizationRequestCache.get("feature:"+code,()->features.isEnabled(code,null));}
 public Rule rule(String code){return rules.get(code);}
 public Set<String> miniappImpliedReads(String code){Rule rule=rules.get(code);return rule==null?Set.of():new HashSet<>(rule.miniappImpliedReads());}
 public Set<String> optionalReads(String code){Rule rule=rules.get(code);return rule==null?Set.of():new HashSet<>(rule.optionalReads());}
 public Set<String> impliedReads(String code){
  Rule rule=rules.get(code);return rule==null?new HashSet<>():new HashSet<>(rule.impliedReads());
 }
 public String generalReason(Permission p){return generalReason(p,PermissionClient.WEB,new HashSet<>());}
 private String generalReason(Permission p,PermissionClient client,Set<String> path){
  if(p.status()!=PermissionStatus.ENABLED)return "权限已停用";
  if(!path.add(p.code()))return "权限依赖存在循环";
  try {
   Rule rule=rules.get(p.code());
   if(client==PermissionClient.WEB && rule==null)return "尚未接入规范接口动作";
   var nodes=AuthorizationRequestCache.get("menus",menus::findAll);
   if(client==PermissionClient.WEB && !PermissionMenuPolicy.enabled(p.code(),nodes))return "菜单未配置或上级目录已停用";
   if(rule!=null) {
    List<String> contextual=rule.features().stream().filter(f->f.contains(":" )).map(f->f.substring(f.indexOf(':')+1)).toList();
    if(!contextual.isEmpty() && contextual.stream().noneMatch(this::featureEnabled))return "相关功能开关均未启用："+String.join("、",contextual);
    for(String feature:rule.features())if(!feature.contains(":") && !featureEnabled(feature))return "功能开关未启用："+feature;
   }
   if(client==PermissionClient.WEB && permissions!=null){
    Set<String> required=PermissionMenuPolicy.necessaryPages(Set.of(p.code()),nodes);
    required.addAll(impliedReads(p.code()));
    for(String code:required){
     var read=AuthorizationRequestCache.get("permission:"+code,()->permissions.findByCode(code));
     if(read==null)return "缺少必要读取动作："+code;
     String reason=generalReason(read,client,path);if(reason!=null)return "必要读取不可用："+code+"，"+reason;
    }
   }
   return null;
  } finally{path.remove(p.code());}
 }
 public String identityReason(Permission p,Long userId){return identityReason(p,userId,null);}
 public String identityReason(Permission p,Long userId,String candidateRoleCode){
  var user=AuthorizationRequestCache.get("user:"+userId,()->users.findById(userId));if(user==null)return "用户不存在";
  Rule rule=rules.get(p.code());
  if(rule!=null && !rule.allowedRoleCodes().isEmpty() && !(candidateRoleCode!=null && rule.allowedRoleCodes().contains(candidateRoleCode))
      && rule.allowedRoleCodes().stream().noneMatch(code->AuthorizationRequestCache.get("has-role:"+userId+":"+code,()->roles.hasRoleCode(userId,code))))return "仅系统管理员可操作";
  if(rule!=null && rule.actorPrerequisites().contains("teacherProfile") && teacherProfiles!=null && (user.type()!=com.lingdong.learning.user.domain.UserType.ORGANIZATION || user.status()!=com.lingdong.learning.user.domain.UserStatus.ENABLED || !AuthorizationRequestCache.get("teacher-profile:"+userId,()->teacherProfiles.enabled(userId))))return "需要真实教师职业档案";
  boolean familyIdentity=user.type()!=com.lingdong.learning.user.domain.UserType.STUDENT && (AuthorizationRequestCache.get("parent:"+userId,()->parents.findProfileByUserId(userId))!=null || AuthorizationRequestCache.get("has-role:"+userId+":PARENT_ADMIN",()->roles.hasRoleCode(userId,"PARENT_ADMIN")));
  boolean institutionIdentity=user.type()!=com.lingdong.learning.user.domain.UserType.STUDENT && AuthorizationRequestCache.get("has-role:"+userId+":ORG_ADMIN",()->roles.hasRoleCode(userId,"ORG_ADMIN"));
  if(rule!=null && !rule.allowedUserTypes().isEmpty() && !rule.allowedUserTypes().contains(user.type().name()) && !(rule.allowedUserTypes().contains("FAMILY")&&familyIdentity) && !(rule.allowedUserTypes().contains("ORGANIZATION")&&institutionIdentity))return "账号身份不适用："+String.join("、",rule.allowedUserTypes());
  if(rule!=null && rule.allowedUserTypes().equals(List.of("FAMILY")) && !familyIdentity)return "缺少家长档案或有效家庭管理员身份";
  return null;
 }
 public String userReason(Permission p,Long userId){return userReason(p,userId,PermissionClient.WEB);}
 public String userReason(Permission p,Long userId,PermissionClient client){
  if(client==PermissionClient.MINIAPP)return null; // Mobile endpoints retain their own feature and resource guards.
  var account=AuthorizationRequestCache.get("user:"+userId,()->users.findById(userId));
  if(client==PermissionClient.WEB && account!=null && account.type()==com.lingdong.learning.user.domain.UserType.STUDENT)return "学生账号仅适用于小程序";
  String reason=generalReason(p,client,new HashSet<>());if(reason!=null)return reason;
  reason=identityReason(p,userId);if(reason!=null)return reason;
  Rule rule=rules.get(p.code());
  if(client==PermissionClient.WEB && permissions!=null)for(String code:impliedReads(p.code(),userId)){
   var read=AuthorizationRequestCache.get("permission:"+code,()->permissions.findByCode(code));
   if(read==null)return "缺少必要读取动作："+code;
   String readReason=generalReason(read,client,new HashSet<>());if(readReason!=null)return "必要读取不可用："+code+"，"+readReason;
  }
  if(rule!=null && rule.scopeKinds().equals(List.of("ALL")) && scopes!=null && !AuthorizationRequestCache.get("scope:"+userId,()->scopes.resolve(userId)).allOrganizations())return "需要全局数据范围";
  if(rule!=null && rule.scopeKinds().contains("CUSTOM") && scopes!=null) {
   var scope=AuthorizationRequestCache.get("scope:"+userId,()->scopes.resolve(userId));
   if(!scope.allOrganizations() && scope.rootPaths().isEmpty())return "需要有效的组织数据范围";
  }
  return null;
 }
 public String roleReason(Permission p,Long roleId){
  String reason=generalReason(p);if(reason!=null)return reason;
  var role=AuthorizationRequestCache.get("role:"+roleId,()->roleCatalog.findById(roleId));if(role==null)return "角色不存在";
  if("STUDENT".equals(role.code()))return "学生账号仅适用于小程序";
  Rule rule=rules.get(p.code());
  if(rule==null)return null;
  if(rule.scopeKinds().equals(List.of("ALL")) && role.dataScope()!=com.lingdong.learning.iam.domain.RoleDataScope.ALL)return "该权限需要全局数据范围";
  if(rule.scopeKinds().contains("CUSTOM") && !rule.scopeKinds().contains(role.dataScope().name()))return "该角色数据范围不支持此组织操作";
  if(!rule.allowedRoleCodes().isEmpty() && !rule.allowedRoleCodes().contains(role.code()))return "仅系统管理员角色可授权";
  if(rule.allowedUserTypes().isEmpty())return null;
  // Built-in role identity is a grant compatibility rule, never an action execution bypass.
  String type=switch(role.code()){case "PARENT","PARENT_ADMIN"->"FAMILY";case "TEACHER","ORG_ADMIN","ORG_MEMBER"->"ORGANIZATION";case "STUDENT"->"STUDENT";case "SYS_ADMIN","SYS_AUDITOR","PLATFORM_USER"->"PLATFORM";default->null;};
  if(type!=null && !rule.allowedUserTypes().contains(type))return "该角色的账号身份不适用";
  for(var assignment:AuthorizationRequestCache.get("role-users:"+roleId,()->roles.findByRoleId(roleId))) {
   String userReason=userReason(p,assignment.userId());if(userReason!=null)return "已关联用户不适用："+userReason;
  }
  return null;
 }
}
