package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.iam.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class UserOrganizationAuthorizationControllerTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserAccessApplicationService users;
 @Autowired RoleMapper roles;
 @Autowired PermissionMapper permissions;
 @Autowired OrganizationApplicationService organizations;
 @Autowired AuthenticationApplicationService auth;
 @Autowired JdbcTemplate jdbc;

 @Test void httpCreationRequiresOrganizationAndAssociatesAtomically() throws Exception {
  String token=admin("new_org_create"); Organization org=org("NEW_ORG_CREATE");
  mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"username\":\"new_org_missing\",\"displayName\":\"新人\",\"type\":\"PLATFORM\"}"))
   .andExpect(status().isBadRequest());
  String body="{\"username\":\"new_org_good\",\"displayName\":\"新人\",\"type\":\"PLATFORM\",\"organizationId\":\"%s\",\"password\":\"Password123\"}".formatted(org.id());
  String id=json.readTree(mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText();
  assertThat(jdbc.queryForObject("select count(*) from sys_user_organization where user_id=? and organization_id=?",Integer.class,id,org.id())).isEqualTo(1);
  mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
   .content(body.replace("new_org_good","new_org_bad").replace(org.id().toString(),"1000000000000000000"))).andExpect(status().isNotFound());
  assertThat(jdbc.queryForObject("select count(*) from sys_user where username='new_org_bad'",Integer.class)).isZero();
 }
 @Test void membersAreDirectPagedAndAdministratorPromotionIsIdempotent() throws Exception {
  String token=admin("member_admin"); Organization org=org("MEMBER_ROOT"); User target=user("member_target");
  String body="{\"userIds\":[\"%s\"],\"administrator\":false}".formatted(target.id());
  for(int i=0;i<2;i++)mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/organizations/{id}/members",org.id()).header("Authorization","Bearer "+token).param("keyword","member_target"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(target.id().toString())).andExpect(jsonPath("$.items[0].administrator").value(false));
  for(int i=0;i<2;i++)mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body.replace("false","true"))).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/organizations/{id}/members",org.id()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].administrator").value(true));
  assertThat(jdbc.queryForObject("select count(*) from sys_user_role where user_id=? and role_id=? and organization_id=?",Integer.class,target.id(),roles.findByCode("ORG_ADMIN").id(),org.id())).isEqualTo(1);
  mvc.perform(get("/api/v1/roles/{id}/users",roles.findByCode("ORG_ADMIN").id()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.userId=='%s')].organizationId".formatted(target.id())).value(org.id().toString()));
 }
 @Test void invalidBatchMemberRollsBackWholeBatch() throws Exception {
  String token=admin("batch_invalid_admin");Organization org=org("BATCH_INVALID");User good=user("batch_good");User bad=user("batch_bad");
  jdbc.update("update sys_user set status='DISABLED' where id=?",bad.id());
  mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"userIds\":[\"%s\",\"%s\"],\"administrator\":true}".formatted(good.id(),bad.id()))).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("select count(*) from sys_user_organization where organization_id=?",Integer.class,org.id())).isZero();
 }
 @Test void userTreeAndBatchPreserveDeniesAndRejectStaleSnapshot() throws Exception {
  String token=admin("tree_admin");User target=user("tree_target");Role role=roles.findByCode("SYS_AUDITOR");users.assignRole(new AssignRoleToUserCommand(target.id(),role.id(),null));
  Long pid=permissions.findByCode("IAM_USER_CREATE").id();Long deny=permissions.findByCode("IAM_USER_STATUS_CHANGE").id();
  jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'DENY')",990000000000000001L,target.id(),deny);
  mvc.perform(get("/api/v1/users/{id}/permission-tree",target.id()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.menus").isArray()).andExpect(jsonPath("$.inheritedPermissionIds").isArray()).andExpect(jsonPath("$.inheritedDeniedPermissionIds").isArray());
  String body="{\"permissionIds\":[\"%s\"],\"managedPermissionIds\":[\"%s\",\"%s\"],\"expectedAssignments\":[{\"permissionId\":\"%s\",\"effect\":\"DENY\"}]}".formatted(pid,pid,deny,deny);
  mvc.perform(put("/api/v1/users/{id}/permissions:batch",target.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
  mvc.perform(put("/api/v1/users/{id}/permissions:batch",target.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("select effect from sys_user_permission where user_id=? and permission_id=?",String.class,target.id(),deny)).isEqualTo("DENY");
 }
 @Test void inheritedRoleDeniesWinAndCannotBeEditedThroughUserBatch() throws Exception {
  String token=admin("inherit_admin");User target=user("inherit_target");
  Role role=Role.custom(990000000000000011L,"INHERIT_CUSTOM_ROLE","继承角色",null,RoleDataScope.ALL);roles.insert(role);
  users.assignRole(new AssignRoleToUserCommand(target.id(),role.id(),null));
  Long allow=permissions.findByCode("IAM_USER_READ").id();Long deny=permissions.findByCode("IAM_USER_CREATE").id();
  jdbc.update("insert into sys_role_permission(id,role_id,permission_id,effect) values(?,?,?,'ALLOW')",990000000000000012L,role.id(),allow);
  jdbc.update("insert into sys_role_permission(id,role_id,permission_id,effect) values(?,?,?,'DENY')",990000000000000013L,role.id(),deny);
  mvc.perform(get("/api/v1/users/{id}/permission-tree",target.id()).header("Authorization","Bearer "+token)).andExpect(status().isOk())
   .andExpect(jsonPath("$.inheritedPermissionIds[0]").value(allow.toString())).andExpect(jsonPath("$.inheritedDeniedPermissionIds[0]").value(deny.toString()));
  for(Long pid:java.util.List.of(allow,deny)) mvc.perform(put("/api/v1/users/{id}/permissions:batch",target.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"permissionIds\":[\"%s\"],\"managedPermissionIds\":[\"%s\"],\"expectedAssignments\":[]}".formatted(pid,pid))).andExpect(status().isConflict());
  jdbc.update("update sys_role set status='DISABLED' where id=?",role.id());
  mvc.perform(get("/api/v1/users/{id}/permission-tree",target.id()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.inheritedPermissionIds").isEmpty()).andExpect(jsonPath("$.inheritedDeniedPermissionIds").isEmpty());
 }
 @Test void organizationMemberOperationsRejectAuditorsMissingAdditionalPermissionsAndIneffectiveOrganizations() throws Exception {
  String token=admin("member_security_admin");Organization org=org("MEMBER_SECURITY");User target=user("member_security_target");
  Long operator=jdbc.queryForObject("select id from sys_user where username='member_security_admin'",Long.class);
  Long pid=permissions.findByCode("IAM_DATA_SCOPE_CONFIGURE").id();
  jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'DENY')",990000000000000020L,operator,pid);
  String body="{\"userIds\":[\"%s\"],\"administrator\":true}".formatted(target.id());
  mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("select count(*) from sys_user_organization where organization_id=?",Integer.class,org.id())).isZero();
  jdbc.update("delete from sys_user_permission where user_id=? and permission_id=?",operator,pid);
  users.assignRole(new AssignRoleToUserCommand(operator,roles.findByCode("SYS_AUDITOR").id(),null));
  mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body.replace("true","false"))).andExpect(status().isForbidden());
  jdbc.update("delete from sys_user_role where user_id=? and role_id=?",operator,roles.findByCode("SYS_AUDITOR").id());
  jdbc.update("update sys_organization set effective_status='DISABLED' where id=?",org.id());
  mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
 }
 @Test void concurrentUserBatchSavesAcceptOnlyOneSnapshot() throws Exception {
  String token=admin("user_race_admin");User target=user("user_race_target");
  users.assignRole(new AssignRoleToUserCommand(target.id(),roles.findByCode("SYS_AUDITOR").id(),null));
  Long pid=permissions.findByCode("IAM_USER_CREATE").id();
  String body="{\"permissionIds\":[\"%s\"],\"managedPermissionIds\":[\"%s\"],\"expectedAssignments\":[]}".formatted(pid,pid);
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Callable<Integer> save=()->{start.await();return mvc.perform(put("/api/v1/users/{id}/permissions:batch",target.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();};
  try{var a=executor.submit(save);var b=executor.submit(save);start.countDown();assertThat(java.util.List.of(a.get(15,java.util.concurrent.TimeUnit.SECONDS),b.get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);}finally{executor.shutdownNow();}
  assertThat(jdbc.queryForObject("select count(*) from sys_iam_change_audit where target_id=? and event_type='USER_PERMISSION_CONFIGURE'",Integer.class,target.id())).isEqualTo(1);
 }
 @Test void boundedMemberRelationsReturnOnlyDirectExistingMembersAndRequireAdministrator() throws Exception {
  String token=admin("relations_admin");Organization org=org("RELATIONS_ROOT");User member=user("relations_member");User administrator=user("relations_manager");User absent=user("relations_absent");
  for(User target:java.util.List.of(member,administrator))mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
   .content("{\"userIds\":[\"%s\"],\"administrator\":%s}".formatted(target.id(),target==administrator))).andExpect(status().isNoContent());
  String ids=member.id()+","+administrator.id()+","+absent.id();
  mvc.perform(get("/api/v1/organizations/{id}/member-relations",org.id()).header("Authorization","Bearer "+token).param("userIds",ids)).andExpect(status().isOk())
   .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].userId").value(member.id().toString())).andExpect(jsonPath("$[0].administrator").value(false))
   .andExpect(jsonPath("$[1].userId").value(administrator.id().toString())).andExpect(jsonPath("$[1].administrator").value(true));
  mvc.perform(get("/api/v1/organizations/{id}/member-relations",org.id()).header("Authorization","Bearer "+token).param("userIds",member.id()+","+member.id())).andExpect(status().isBadRequest());
  String tooMany=java.util.stream.LongStream.range(1,102).mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(","));
  mvc.perform(get("/api/v1/organizations/{id}/member-relations",org.id()).header("Authorization","Bearer "+token).param("userIds",tooMany)).andExpect(status().isBadRequest());
  Role reader=Role.custom(990000000000000031L,"RELATION_READER","成员查看角色",null,RoleDataScope.ALL);roles.insert(reader);
  jdbc.update("insert into sys_role_permission(id,role_id,permission_id,effect) values(?,?,?,'ALLOW')",990000000000000032L,reader.id(),permissions.findByCode("IAM_USER_LIST").id());
  users.assignRole(new AssignRoleToUserCommand(absent.id(),reader.id(),null));
  Long adminId=jdbc.queryForObject("select id from sys_user where username='relations_admin'",Long.class);
  auth.setPlatformUserPassword(new SetPlatformUserPasswordCommand(adminId,absent.id(),"Password123"));
  String ordinary=json.readTree(mvc.perform(post("/api/v1/auth/sessions/password").contentType(MediaType.APPLICATION_JSON)
   .content("{\"username\":\"relations_absent\",\"password\":\"Password123\",\"deviceId\":\"relations-absent-device\",\"deviceName\":\"test\"}"))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
  mvc.perform(get("/api/v1/organizations/{id}/member-relations",org.id()).header("Authorization","Bearer "+ordinary).param("userIds",ids)).andExpect(status().isForbidden());
 }
 @Test void managedPasswordCreationResetAndOrganizationFilter() throws Exception {
  String token=admin("managed_password_admin"); Organization org=org("MANAGED_PASSWORD_ORG"); Organization other=org("MANAGED_PASSWORD_OTHER");
  String body="{\"username\":\"managed_password_family\",\"displayName\":\"家长\",\"type\":\"FAMILY\",\"organizationId\":\"%s\",\"password\":\"FamilyPass123\"}".formatted(org.id());
  String id=json.readTree(mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist()).andReturn().getResponse().getContentAsString()).path("id").asText();
  users.assignRole(new AssignRoleToUserCommand(Long.valueOf(id),roles.findByCode("PARENT").id(),null));
  jdbc.update("insert into auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at) values(?,?,'COMPLETED',current_timestamp,current_timestamp)",990000000000000040L,Long.valueOf(id));
  AuthenticatedSession session=auth.loginByPassword(new PasswordLoginCommand("managed_password_family","FamilyPass123","managed-family-device","test"));
  AuthenticatedSession miniSession=auth.loginByPassword(new PasswordLoginCommand("managed_password_family","FamilyPass123","managed-mini-device","test"));
  jdbc.update("update auth_device_session set client_type='MINIAPP' where id=?",miniSession.sessionId());
  assertThat(jdbc.queryForObject("select password_hash from sys_user where id=?",String.class,id)).startsWith("$2");
  mvc.perform(post("/api/v1/users/{id}/password",id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"NewFamily123\"}")).andExpect(status().isNoContent());
  assertThatThrownBy(()->auth.authenticateAccessToken(session.accessToken())).isInstanceOf(AuthenticationFailedException.class);
  assertThatThrownBy(()->auth.refreshSession(new RefreshSessionCommand(session.refreshToken()))).isInstanceOf(AuthenticationFailedException.class);
  assertThatThrownBy(()->auth.authenticateAccessToken(miniSession.accessToken())).isInstanceOf(AuthenticationFailedException.class);
  assertThat(jdbc.queryForObject("select count(*) from auth_device_session where user_id=? and status='ACTIVE'",Integer.class,id)).isZero();
  assertThatThrownBy(()->auth.loginByPassword(new PasswordLoginCommand("managed_password_family","FamilyPass123","old-device","test"))).isInstanceOf(AuthenticationFailedException.class);
  assertThat(auth.authenticateAccessToken(auth.loginByPassword(new PasswordLoginCommand("managed_password_family","NewFamily123","new-device","test")).accessToken()).userId()).isEqualTo(Long.valueOf(id));
  assertThat(jdbc.queryForObject("select count(*) from sys_iam_change_audit where target_id=? and event_type='USER_PASSWORD_RESET'",Integer.class,id)).isEqualTo(1);
  mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token).param("organizationId",org.id().toString()).param("pageSize","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
  mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token).param("organizationId",other.id().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  for(String password:java.util.List.of("weakpass",""))mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body.replace("managed_password_family","managed_invalid_family").replace("FamilyPass123",password))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/users").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body.replace("managed_password_family","managed_missing_family").replace(",\"password\":\"FamilyPass123\"", ""))).andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("select count(*) from sys_user where username='managed_invalid_family'",Integer.class)).isZero();
  assertThat(jdbc.queryForObject("select count(*) from sys_user where username='managed_missing_family'",Integer.class)).isZero();
 }
 @Test void resetRejectsStudentCancelledAccountsAndDeniedPermission() throws Exception {
  String token=admin("reset_boundary_admin"); Long operator=jdbc.queryForObject("select id from sys_user where username='reset_boundary_admin'",Long.class);
  User student=users.createUser(new CreateUserCommand("reset_boundary_student","student",null,UserType.STUDENT)); User cancelled=user("reset_boundary_cancelled"); User target=user("reset_boundary_target");
  jdbc.update("update sys_user set status='CANCELLED' where id=?",cancelled.id());
  for(User denied:java.util.List.of(student,cancelled))mvc.perform(post("/api/v1/users/{id}/password",denied.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Password123\"}")).andExpect(status().isBadRequest());
  Long permission=permissions.findByCode("IAM_USER_PASSWORD_SET").id();
  jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'DENY')",990000000000000041L,operator,permission);
  mvc.perform(post("/api/v1/users/{id}/password",target.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Password123\"}")).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("select password_hash from sys_user where id=?",String.class,target.id())).isNull();
  assertThat(new CreateUserRequest("user","user",null,UserType.PLATFORM,1L,"Password123").toString()).doesNotContain("Password123");
  assertThat(new SetPlatformUserPasswordCommand(operator,target.id(),"Password123").toString()).doesNotContain("Password123");
 }
 @Test void organizationDirectoryMatchesDirectMembersOnceAndPaginatesAdministrators() throws Exception {
  String token=admin("directory_filter_admin"); Organization org=org("DIRECTORY_FILTER_ORG"); Organization other=org("DIRECTORY_FILTER_OTHER"); User member=user("directory_filter_member"); User manager=user("directory_filter_manager");
  users.associateWithOrganization(new AssociateUserWithOrganizationCommand(member.id(),org.id()));
  users.associateWithOrganization(new AssociateUserWithOrganizationCommand(member.id(),other.id()));
  mvc.perform(post("/api/v1/organizations/{id}/members:batch",org.id()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[\"%s\"],\"administrator\":true}".formatted(manager.id()))).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token).param("organizationId",org.id().toString()).param("pageSize","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token).param("organizationId",org.id().toString()).param("page","2").param("pageSize","1")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token).param("organizationId",org.id().toString()).param("keyword","directory_filter_manager")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(manager.id().toString()));
  mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+token).param("organizationId",other.id().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
 }
 private User user(String name){return users.createUser(new CreateUserCommand(name,name,null,UserType.PLATFORM));}
 private Organization org(String code){return organizations.createOrganization(new CreateOrganizationCommand(code,code,"REGION",null,0));}
 private String admin(String name)throws Exception{User u=user(name);users.assignRole(new AssignRoleToUserCommand(u.id(),roles.findByCode("SYS_ADMIN").id(),null));auth.setPlatformUserPassword(new SetPlatformUserPasswordCommand(u.id(),u.id(),"Password123"));return json.readTree(mvc.perform(post("/api/v1/auth/sessions/password").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\",\"password\":\"Password123\",\"deviceId\":\"%s-device\",\"deviceName\":\"test\"}".formatted(name,name))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();}
}

