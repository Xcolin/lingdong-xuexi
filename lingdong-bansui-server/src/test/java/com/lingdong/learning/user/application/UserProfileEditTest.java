package com.lingdong.learning.user.application;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class UserProfileEditTest {
 @Autowired UserAccessApplicationService users; @Autowired RoleMapper roles; @Autowired OrganizationApplicationService organizations; @Autowired JdbcTemplate jdbc;
 User account(String name,UserType type,String mobile){return users.createUser(new CreateUserCommand(name,name,mobile,type));}
 User admin(){User a=account("profile_editor",UserType.PLATFORM,null);users.assignRole(new AssignRoleToUserCommand(a.id(),roles.findByCode("SYS_ADMIN").id(),null));return a;}
 @Test void updatesStudentNameAtomicallyWithoutChangingIdentityOrRelations(){
  User actor=admin(),target=account("profile_student",UserType.STUDENT,null);
  jdbc.update("insert into edu_student(id,student_name,student_user_id) values (?,?,?)",987001L,"原学生名",target.id());
  jdbc.update("update sys_user set password_hash='untouched-hash' where id=?",target.id());
  users.assignRole(new AssignRoleToUserCommand(target.id(),roles.findByCode("STUDENT").id(),null));
  var updated=users.updateProfile(actor.id(),target.id()," 新学生名 ",null);
  assertThat(updated.username()).isEqualTo(target.username());assertThat(updated.type()).isEqualTo(UserType.STUDENT);assertThat(updated.status()).isEqualTo(UserStatus.ENABLED);
  assertThat(updated.passwordHash()).isEqualTo("untouched-hash");assertThat(updated.displayName()).isEqualTo("新学生名");
  assertThat(jdbc.queryForObject("select student_name from edu_student where student_user_id=?",String.class,target.id())).isEqualTo("新学生名");
  assertThat(jdbc.queryForObject("select count(*) from sys_user_role where user_id=?",Integer.class,target.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from sys_iam_change_audit where event_type='USER_PROFILE_CHANGE' and target_id=?",Integer.class,target.id())).isEqualTo(1);
 }
 @Test void rejectsMissingPermissionAndForeignSchoolEvenWithEditPermission(){
  User actor=account("profile_school_actor",UserType.ORGANIZATION,null),target=account("profile_foreign",UserType.ORGANIZATION,null);
  assertThatThrownBy(()->users.updateProfile(actor.id(),target.id(),"拒绝",null)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  Organization a=organizations.createOrganization(new CreateOrganizationCommand("PROFILE_A","学校A","SCHOOL",null,10));
  Organization b=organizations.createOrganization(new CreateOrganizationCommand("PROFILE_B","学校B","SCHOOL",null,20));
  users.associateWithOrganization(new AssociateUserWithOrganizationCommand(actor.id(),a.id()));users.associateWithOrganization(new AssociateUserWithOrganizationCommand(target.id(),b.id()));
  users.assignRole(new AssignRoleToUserCommand(actor.id(),roles.findByCode("ORG_ADMIN").id(),a.id()));
  assertThatThrownBy(()->users.updateProfile(actor.id(),target.id(),"越校编辑",null)).isInstanceOf(com.lingdong.learning.common.security.SystemOperationAccessDeniedException.class);
  assertThat(jdbc.queryForObject("select display_name from sys_user where id=?",String.class,target.id())).isEqualTo(target.displayName());
 }
 @Test void rejectsDuplicateMobileAndCancelledAccountAndAllowsClearingMobile(){
  User actor=admin(),target=account("profile_mobile",UserType.PLATFORM,"13801000001");account("profile_mobile_other",UserType.PLATFORM,"13801000002");
  assertThatThrownBy(()->users.updateProfile(actor.id(),target.id(),"新名","13801000002")).isInstanceOf(DuplicateUserAccountException.class);
  assertThat(users.updateProfile(actor.id(),target.id(),"新名",null).mobile()).isNull();
  jdbc.update("update sys_user set status='CANCELLED' where id=?",target.id());
  assertThatThrownBy(()->users.updateProfile(actor.id(),target.id(),"再次编辑",null)).isInstanceOf(IllegalStateException.class);
 }
}
