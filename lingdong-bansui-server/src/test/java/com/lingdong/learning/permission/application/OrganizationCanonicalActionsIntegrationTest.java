package com.lingdong.learning.permission.application;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(properties="mybatis.configuration.local-cache-scope=STATEMENT") @ActiveProfiles("test") @Transactional
class OrganizationCanonicalActionsIntegrationTest {
 @Autowired PermissionDecisionService decisions;@Autowired UserAccessApplicationService users;@Autowired RoleMapper roles;@Autowired OrganizationMapper orgs;@Autowired OrganizationDataScopeService scopes;@Autowired IdGenerator ids;@Autowired JdbcTemplate jdbc;
 @Autowired com.lingdong.learning.iam.application.OrganizationMembershipService members;
 @Autowired com.lingdong.learning.organization.application.OrganizationManagementApplicationService management;
 @Autowired com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper grants;
 @org.junit.jupiter.api.BeforeEach void directServiceCallsHaveNoHttpSnapshot() throws Exception {
  org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
  jdbc.update("UPDATE sys_menu SET parent_id=NULL");jdbc.update("DELETE FROM sys_menu");
  var model=new com.fasterxml.jackson.databind.ObjectMapper().readTree(new org.springframework.core.io.ClassPathResource("authorization-model.json").getInputStream());
  java.util.Map<String,Long> pageIds=new java.util.HashMap<>();
  for(var page:model.path("pages")){
   String code=page.path("permissionCode").asText();Long id=ids.nextId();pageIds.put(page.path("route").asText(),id);
   permission(code,"PAGE");
   jdbc.update("INSERT INTO sys_menu(id,code,name,type,route,permission_code,grantable,sort_order,status,version) VALUES(?,?,?,'PAGE',?,?,1,0,'ENABLED',0)",id,code,code,page.path("route").asText(),code);
  }
  for(var action:model.path("actions")){
   String code=action.path("code").asText();permission(code,"BUTTON");
   jdbc.update("INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version) VALUES(?,?,?,'BUTTON',?,?,1,0,'ENABLED',0)",ids.nextId(),code,code,pageIds.get(action.path("route").asText()),code);
  }
 }
 private void permission(String code,String type){
  if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code=?",Integer.class,code)==0)
   jdbc.update("INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,status) VALUES(?,?,?,?,'WEB','ENABLED')",ids.nextId(),code,code,type);
 }

 @Test void parentSeesOwnFamilyAndAdminGetsFamilyBoundRoleThenCancellationRemovesIt(){
  String suffix=UUID.randomUUID().toString().substring(0,8);var actor=users.createUser(new CreateUserCommand("family_admin_op_"+suffix,"系统管理员",null,UserType.PLATFORM));users.assignRole(new AssignRoleToUserCommand(actor.id(),roles.findByCode("SYS_ADMIN").id(),null));
  var parent=users.createUser(new CreateUserCommand("family_parent_"+suffix,"家长",null,UserType.FAMILY));users.assignRole(new AssignRoleToUserCommand(parent.id(),roles.findByCode("PARENT").id(),null));
  parentProfile(parent.id());
  Long familyId=ids.nextId(),otherId=ids.nextId();orgs.insert(Organization.create(familyId,null,"ROOT","FAMILY_"+suffix,"家庭"+suffix,"FAMILY","/"+familyId+"/",0,null));orgs.insert(Organization.create(otherId,null,"ROOT","FOREIGN_"+suffix,"另一家庭"+suffix,"FAMILY","/"+otherId+"/",0,null));users.associateWithOrganization(new AssociateUserWithOrganizationCommand(parent.id(),familyId));
  jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,p.id,'ALLOW' FROM sys_permission p WHERE p.permission_code='ORG_NODE_READ' AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=? AND rp.permission_id=p.id)",ids.nextId(),roles.findByCode("PARENT").id(),roles.findByCode("PARENT").id());
  jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='ORGANIZATION_MANAGEMENT' AND scope_key='GLOBAL'");
  assertThat(management.listOrganizations(parent.id())).extracting(Organization::id).contains(familyId).doesNotContain(otherId);
  jdbc.update("INSERT INTO sys_role(id,role_code,role_name,role_type,data_scope,built_in,status) VALUES(?,'PARENT_ADMIN','家庭管理员','BUILT_IN','FAMILY',1,'ENABLED')",ids.nextId());
  seedCanonical();
  members.addMembers(actor.id(),familyId,java.util.List.of(parent.id()),true);
  assertThat(grants.hasRoleCode(parent.id(),"PARENT_ADMIN")).isTrue();assertThat(scopes.canAccess(parent.id(),familyId)).isTrue();assertThat(scopes.canAccess(parent.id(),otherId)).isFalse();


  Long adminRoleId=roles.findByCode("PARENT_ADMIN").id();
  for(String code:java.util.List.of("ORG_MEMBER_READ","ORG_ADMIN_ASSIGN","ORG_ADMIN_REVOKE"))
   jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,id,'ALLOW' FROM sys_permission WHERE permission_code=?",ids.nextId(),adminRoleId,code);
  assertThat(decisions.isAllowed(parent.id(),PermissionClient.WEB,"IAM_DATA_SCOPE_CONFIGURE")).isFalse();
  assertThat(decisions.isAllowed(parent.id(),PermissionClient.WEB,"IAM_USER_ROLE_ASSIGN")).isFalse();
  var delegated=users.createUser(new CreateUserCommand("delegated_parent_"+suffix,"另一家长",null,UserType.FAMILY));users.assignRole(new AssignRoleToUserCommand(delegated.id(),roles.findByCode("PARENT").id(),null));users.associateWithOrganization(new AssociateUserWithOrganizationCommand(delegated.id(),familyId));parentProfile(delegated.id());
  members.addMembers(parent.id(),familyId,java.util.List.of(delegated.id()),true);
  assertThat(grants.hasRoleCode(delegated.id(),"PARENT_ADMIN")).isTrue();
  org.assertj.core.api.Assertions.assertThatThrownBy(()->members.addMembers(parent.id(),otherId,java.util.List.of(delegated.id()),true)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  members.revokeMembers(parent.id(),familyId,java.util.List.of(delegated.id()),true);
  assertThat(grants.hasRoleCode(delegated.id(),"PARENT_ADMIN")).isFalse();
  jdbc.update("INSERT INTO sys_user_permission(id,user_id,permission_id,effect) SELECT ?,?,id,'DENY' FROM sys_permission WHERE permission_code='ORG_ADMIN_ASSIGN'",ids.nextId(),actor.id());
  assertThat(decisions.isAllowed(actor.id(),PermissionClient.WEB,"IAM_DATA_SCOPE_CONFIGURE")).isTrue();
  assertThat(decisions.isAllowed(actor.id(),PermissionClient.WEB,"IAM_USER_ROLE_ASSIGN")).isTrue();
  org.assertj.core.api.Assertions.assertThatThrownBy(()->members.addMembers(actor.id(),familyId,java.util.List.of(delegated.id()),true)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  assertThat(grants.hasRoleCode(delegated.id(),"PARENT_ADMIN")).isFalse();
  jdbc.update("UPDATE sys_role SET status='DISABLED' WHERE role_code='PARENT_ADMIN'");
  members.revokeMembers(actor.id(),familyId,java.util.List.of(parent.id()),true);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.role_code='PARENT_ADMIN'",Integer.class,parent.id())).isZero();
  assertThat(grants.hasRoleCode(parent.id(),"PARENT_ADMIN")).isFalse();assertThat(grants.hasRoleCode(parent.id(),"PARENT")).isTrue();assertThat(scopes.canAccess(parent.id(),familyId)).isFalse();
 }
 private void parentProfile(Long id){jdbc.update("INSERT INTO auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at) VALUES(?,?,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",ids.nextId(),id);}
 private void seedCanonical(){
  for(String code:java.util.List.of("ORG_MEMBER_READ","ORG_MEMBER_ASSOCIATE","ORG_MEMBER_REMOVE","ORG_ADMIN_ASSIGN","ORG_ADMIN_REVOKE")){
   permission(code,"BUTTON");
   jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,id,'ALLOW' FROM sys_permission WHERE permission_code=?",ids.nextId(),roles.findByCode("SYS_ADMIN").id(),code);
  }
 }
}
