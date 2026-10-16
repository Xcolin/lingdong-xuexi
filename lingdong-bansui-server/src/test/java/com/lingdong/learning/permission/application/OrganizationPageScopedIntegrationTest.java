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
@SpringBootTest @ActiveProfiles("test") @Transactional
class OrganizationPageScopedIntegrationTest {
 @Autowired PermissionDecisionService decisions;@Autowired UserAccessApplicationService users;@Autowired RoleMapper roles;@Autowired OrganizationMapper orgs;@Autowired OrganizationDataScopeService scopes;@Autowired IdGenerator ids;@Autowired JdbcTemplate jdbc;
 @Test void organizationButtonDoesNotExpandParentSelfScope(){
  String suffix=UUID.randomUUID().toString().substring(0,8);var user=users.createUser(new CreateUserCommand("scope_parent_"+suffix,"家长",null,UserType.FAMILY));var role=roles.findByCode("PARENT");users.assignRole(new AssignRoleToUserCommand(user.id(),role.id(),null));
  Long ownId=ids.nextId(),foreignId=ids.nextId();orgs.insert(Organization.create(ownId,null,"ROOT","FAM_"+suffix,"自己的家庭"+suffix,"FAMILY","/"+ownId+"/",0,null));orgs.insert(Organization.create(foreignId,null,"ROOT","OTHER_"+suffix,"其他家庭"+suffix,"FAMILY","/"+foreignId+"/",0,null));users.associateWithOrganization(new AssociateUserWithOrganizationCommand(user.id(),ownId));
  jdbc.update("DELETE FROM sys_role_permission WHERE role_id=? AND permission_id IN(SELECT id FROM sys_permission WHERE permission_code='ORG_NODE_READ')",role.id());
  jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,p.id,'ALLOW' FROM sys_permission p WHERE p.permission_code='organizations.members.query' AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=? AND rp.permission_id=p.id)",ids.nextId(),role.id(),role.id());
  assertThat(decisions.findAllowedCodes(user.id(),PermissionClient.WEB)).contains("ORG_NODE_READ");assertThat(decisions.isAllowed(user.id(),PermissionClient.WEB,"ORG_NODE_READ")).isTrue();assertThat(scopes.resolve(user.id()).selfAllowed()).isTrue();assertThat(scopes.resolve(user.id()).allOrganizations()).isFalse();assertThat(scopes.findAccessibleOrganizations(user.id())).extracting(Organization::id).doesNotContain(ownId,foreignId);
 }

 @Autowired com.lingdong.learning.iam.application.OrganizationMembershipService members;
 @Autowired com.lingdong.learning.organization.application.OrganizationManagementApplicationService management;
 @Autowired com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper grants;
 @Test void parentSeesOwnFamilyAndAdminGetsFamilyBoundRoleThenCancellationRemovesIt(){
  String suffix=UUID.randomUUID().toString().substring(0,8);var actor=users.createUser(new CreateUserCommand("family_admin_op_"+suffix,"系统管理员",null,UserType.PLATFORM));users.assignRole(new AssignRoleToUserCommand(actor.id(),roles.findByCode("SYS_ADMIN").id(),null));
  var parent=users.createUser(new CreateUserCommand("family_parent_"+suffix,"家长",null,UserType.FAMILY));users.assignRole(new AssignRoleToUserCommand(parent.id(),roles.findByCode("PARENT").id(),null));
  Long familyId=ids.nextId(),otherId=ids.nextId();orgs.insert(Organization.create(familyId,null,"ROOT","FAMILY_"+suffix,"家庭"+suffix,"FAMILY","/"+familyId+"/",0,null));orgs.insert(Organization.create(otherId,null,"ROOT","FOREIGN_"+suffix,"另一家庭"+suffix,"FAMILY","/"+otherId+"/",0,null));users.associateWithOrganization(new AssociateUserWithOrganizationCommand(parent.id(),familyId));
  jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,p.id,'ALLOW' FROM sys_permission p WHERE p.permission_code='organizations.members.query' AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=? AND rp.permission_id=p.id)",ids.nextId(),roles.findByCode("PARENT").id(),roles.findByCode("PARENT").id());
  jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='ORGANIZATION_MANAGEMENT' AND scope_key='GLOBAL'");
  assertThat(management.listOrganizations(parent.id())).extracting(Organization::id).contains(familyId).doesNotContain(otherId);
  jdbc.update("INSERT INTO sys_role(id,role_code,role_name,role_type,data_scope,built_in,status) VALUES(?,'PARENT_ADMIN','家庭管理员','BUILT_IN','FAMILY',1,'ENABLED')",ids.nextId());
  members.addMembers(actor.id(),familyId,java.util.List.of(parent.id()),true);
  assertThat(grants.hasRoleCode(parent.id(),"PARENT_ADMIN")).isTrue();assertThat(scopes.canAccess(parent.id(),familyId)).isTrue();assertThat(scopes.canAccess(parent.id(),otherId)).isFalse();
  assertThat(members.members(parent.id(),familyId,null,1,10).items()).hasSize(1);
  org.assertj.core.api.Assertions.assertThatThrownBy(()->members.members(parent.id(),otherId,null,1,10)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  Long adminRoleId=roles.findByCode("PARENT_ADMIN").id();
  for(String code:java.util.List.of("IAM_USER_ORGANIZATION_ASSIGN","organizations.members.promote","organizations.members.revoke-admin"))
   jdbc.update("INSERT INTO sys_role_permission(id,role_id,permission_id,effect) SELECT ?,?,id,'ALLOW' FROM sys_permission WHERE permission_code=?",ids.nextId(),adminRoleId,code);
  assertThat(decisions.isAllowed(parent.id(),PermissionClient.WEB,"IAM_DATA_SCOPE_CONFIGURE")).isFalse();
  assertThat(decisions.isAllowed(parent.id(),PermissionClient.WEB,"IAM_USER_ROLE_ASSIGN")).isFalse();
  var delegated=users.createUser(new CreateUserCommand("delegated_parent_"+suffix,"另一家长",null,UserType.FAMILY));users.assignRole(new AssignRoleToUserCommand(delegated.id(),roles.findByCode("PARENT").id(),null));users.associateWithOrganization(new AssociateUserWithOrganizationCommand(delegated.id(),familyId));
  members.addMembers(parent.id(),familyId,java.util.List.of(delegated.id()),true);
  assertThat(grants.hasRoleCode(delegated.id(),"PARENT_ADMIN")).isTrue();
  org.assertj.core.api.Assertions.assertThatThrownBy(()->members.addMembers(parent.id(),otherId,java.util.List.of(delegated.id()),true)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  members.revokeMembers(parent.id(),familyId,java.util.List.of(delegated.id()),true);
  assertThat(grants.hasRoleCode(delegated.id(),"PARENT_ADMIN")).isFalse();
  jdbc.update("UPDATE sys_role SET status='DISABLED' WHERE role_code='PARENT_ADMIN'");
  members.revokeMembers(actor.id(),familyId,java.util.List.of(parent.id()),true);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.role_code='PARENT_ADMIN'",Integer.class,parent.id())).isZero();
  assertThat(grants.hasRoleCode(parent.id(),"PARENT_ADMIN")).isFalse();assertThat(grants.hasRoleCode(parent.id(),"PARENT")).isTrue();assertThat(scopes.canAccess(parent.id(),familyId)).isFalse();
 }
}
