package com.lingdong.learning.iam.application;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.iam.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.*;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationTypeMapper;
import com.lingdong.learning.auth.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class ManagementLifecycleIntegrationTest {
 @Autowired RoleApplicationService roles; @Autowired RoleMapper roleMapper; @Autowired OrganizationApplicationService organizations;
 @Autowired OrganizationTypeLifecycleApplicationService types; @Autowired OrganizationTypeMapper typeMapper; @Autowired UserAccessApplicationService users;
 @Autowired AuthenticationApplicationService auth; @Autowired JdbcTemplate jdbc;
 User admin(){User a=users.createUser(new CreateUserCommand("life_admin","管理员",null,UserType.PLATFORM));users.assignRole(new AssignRoleToUserCommand(a.id(),roleMapper.findByCode("SYS_ADMIN").id(),null));return a;}
 @Test void customRoleCrudAndBuiltinProtection(){
  User actor=admin();Role r=roles.createCustomRole(new CreateCustomRoleCommand("LIFE_CUSTOM","自定义",null,RoleDataScope.SELF,actor.id()));
  assertThat(roles.updateRole(actor.id(),r.id(),"编辑后","说明").name()).isEqualTo("编辑后");
  assertThat(roles.changeRoleStatus(actor.id(),r.id(),RoleStatus.DISABLED).status()).isEqualTo(RoleStatus.DISABLED);
  roles.changeRoleStatus(actor.id(),r.id(),RoleStatus.ENABLED);roles.deleteRole(actor.id(),r.id());assertThat(roleMapper.findById(r.id())).isNull();
  Long builtin=roleMapper.findByCode("SYS_ADMIN").id();
  // V118 权限驱动后内置角色仅系统管理员可编辑：SYS_ADMIN 操作者放行改名，非 SYS_ADMIN 仍拒绝
  assertThat(roles.updateRole(actor.id(),builtin,"系统管理员",null).name()).isEqualTo("系统管理员");
  assertThatThrownBy(()->roles.deleteRole(actor.id(),builtin)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->roles.changeRoleStatus(actor.id(),builtin,RoleStatus.DISABLED)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void roleStatusRevokesSessionsAndBoundRoleCannotDelete(){
  User actor=admin();User target=users.createUser(new CreateUserCommand("life_target","用户",null,UserType.PLATFORM));
  Role r=roles.createCustomRole(new CreateCustomRoleCommand("LIFE_SESSION","会话角色",null,RoleDataScope.SELF,actor.id()));
  users.assignRole(new AssignRoleToUserCommand(target.id(),r.id(),null));
  auth.setPlatformUserPassword(new SetPlatformUserPasswordCommand(actor.id(),target.id(),"Password123"));
  var session=auth.loginByPassword(new PasswordLoginCommand(target.username(),"Password123","life-device","测试"));
  roles.changeRoleStatus(actor.id(),r.id(),RoleStatus.DISABLED);
  assertThat(jdbc.queryForObject("select status from auth_device_session where id=?",String.class,session.sessionId())).isEqualTo("REVOKED");
  assertThatThrownBy(()->roles.deleteRole(actor.id(),r.id())).isInstanceOf(IllegalArgumentException.class);
  assertThat(roleMapper.findById(r.id())).isNotNull();
 }
 @Test void customTypeCrudReferenceAndBuiltinProtection(){
  User actor=admin();OrganizationType t=organizations.createOrganizationType(new CreateOrganizationTypeCommand("LIFE_TYPE","类型",10));
  assertThat(types.update(actor.id(),t.id(),"类型编辑",20).sortOrder()).isEqualTo(20);
  assertThat(types.changeStatus(actor.id(),t.id(),OrganizationStatus.DISABLED).status()).isEqualTo(OrganizationStatus.DISABLED);
  assertThatThrownBy(()->organizations.createOrganization(new CreateOrganizationCommand("LIFE_DISABLED","节点","LIFE_TYPE",null,0))).isInstanceOf(IllegalStateException.class);
  types.changeStatus(actor.id(),t.id(),OrganizationStatus.ENABLED);
  organizations.createOrganization(new CreateOrganizationCommand("LIFE_NODE","节点","LIFE_TYPE",null,0));
  assertThatThrownBy(()->types.delete(actor.id(),t.id())).isInstanceOf(IllegalArgumentException.class);
  OrganizationType free=organizations.createOrganizationType(new CreateOrganizationTypeCommand("LIFE_FREE","未引用",10));types.delete(actor.id(),free.id());assertThat(typeMapper.findById(free.id())).isNull();
  Long builtin=typeMapper.findByCode("SCHOOL").id();
  // 同上：SYS_ADMIN 可编辑内置组织类型名称/排序，启停与删除仍被内置防护拦截
  assertThat(types.update(actor.id(),builtin,"学校",0).name()).isEqualTo("学校");
  assertThatThrownBy(()->types.changeStatus(actor.id(),builtin,OrganizationStatus.DISABLED)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->types.delete(actor.id(),builtin)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void creationTypePolicyAndUserStatusPermissionBoundary(){
  User actor=admin();Organization school=organizations.createOrganization(new CreateOrganizationCommand("LIFE_SCHOOL","学校","SCHOOL",null,0));
  assertThatThrownBy(()->users.createManagedUserResult(new CreateUserCommand("wrong_platform","错误类型",null,UserType.PLATFORM,actor.id()),school.id(),"Password123",null,null,null)).isInstanceOf(IllegalArgumentException.class);
  assertThat(jdbc.queryForObject("select count(*) from sys_user where username='wrong_platform'",Integer.class)).isZero();
  OrganizationUserTypePolicy.validate("FAMILY",UserType.FAMILY);OrganizationUserTypePolicy.validate("GRADE",UserType.STUDENT);OrganizationUserTypePolicy.validate("COUNTRY",UserType.PLATFORM);
  assertThatThrownBy(()->OrganizationUserTypePolicy.validate("FAMILY",UserType.ORGANIZATION)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->OrganizationUserTypePolicy.validate("CITY",UserType.STUDENT)).isInstanceOf(IllegalArgumentException.class);
  User other=users.createUser(new CreateUserCommand("life_no_permission","无权限",null,UserType.PLATFORM));
  assertThatThrownBy(()->users.updateAuthorizedStatus(new UpdateUserStatusCommand(actor.id(),UserStatus.LOCKED,other.id()))).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
 }
}
