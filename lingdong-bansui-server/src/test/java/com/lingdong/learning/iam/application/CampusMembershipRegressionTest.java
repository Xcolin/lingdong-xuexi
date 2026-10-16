package com.lingdong.learning.iam.application;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.application.*;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Transactional
class CampusMembershipRegressionTest {
 @Autowired UserAccessApplicationService users;
 @Autowired OrganizationMembershipService members;
 @Autowired OrganizationApplicationService organizations;
 @Autowired OrganizationDataScopeService scopes;
 @Autowired DataScopeAdministrationService administration;
 @Autowired UserOrganizationMapper memberships;
 @Autowired UserRoleMapper grants;
 @Autowired RoleMapper roles;
 @Autowired PermissionMapper permissions;
 @Autowired JdbcTemplate jdbc;
 @Autowired com.lingdong.learning.teacher.application.TeacherManagementApplicationService teachers;
 @Autowired com.lingdong.learning.student.application.StudentOrganizationLifecycleService studentLifecycle;
 private User user(String name){return users.createUser(new CreateUserCommand(name,name,null,UserType.ORGANIZATION));}
 private User admin(String name){User u=user(name);users.assignRole(new AssignRoleToUserCommand(u.id(),roles.findByCode("SYS_ADMIN").id(),null));return u;}
 private Organization school(String code){Organization region=organizations.createOrganization(new CreateOrganizationCommand(code+"R",code+"R","REGION",null,0));return organizations.createOrganization(new CreateOrganizationCommand(code,code,"SCHOOL",region.id(),0));}
 private Organization campus(Organization school,String code,int order){return organizations.createOrganization(new CreateOrganizationCommand(code,code,"CAMPUS",school.id(),order));}
 private void join(User u,Organization o){users.associateWithOrganization(new AssociateUserWithOrganizationCommand(u.id(),o.id()));}
 @Test void sameSchoolSubtreePickerIsDfsSortedBeforePagingAndRejectsForeignAtomicBatch() {
  User operator=admin("picker_admin");Organization school=school("PICK");Organization later=campus(school,"PICK_LATE",20),early=campus(school,"PICK_EARLY",10);
  User late=user("picker_late"),first=user("picker_early"),foreign=user("picker_foreign"),unbound=user("picker_unbound");join(late,later);join(first,early);join(foreign,school("FOREIGN_PICK"));
  var page=members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",school.id(),"SCHOOL","picker_",1,1);
  assertThat(page.total()).isEqualTo(2);assertThat(page.items()).extracting(m->m.user().id()).containsExactly(first.id());
  assertThat(page.items().get(0).organizationNames().get(0)).contains("PICK_EARLY");
  assertThat(members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",school.id(),"SCHOOL","picker_",2,1).items()).extracting(m->m.user().id()).containsExactly(late.id());
  assertThat(members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",early.id(),"SCHOOL","picker_",1,20).items()).extracting(m->m.user().id()).containsExactly(first.id());
  assertThatThrownBy(()->members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",null,"UNBOUND","picker_unbound",1,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThatThrownBy(()->members.addMembers(operator.id(),school.id(),List.of(first.id(),foreign.id()),false)).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThat(memberships.exists(first.id(),school.id())).isFalse();
 }
 @Test void twoCampusAdministratorsShareSchoolRoleAndCancelOnlyCorrespondingAutomaticGrant() {
  User operator=admin("multi_admin_operator"),target=user("multi_admin_target");Organization school=school("MULTI");Organization a=campus(school,"MULTI_A",0),b=campus(school,"MULTI_B",1);
  members.addMembers(operator.id(),a.id(),List.of(target.id()),true);members.addMembers(operator.id(),b.id(),List.of(target.id()),true);
  Long role=roles.findByCode("ORG_ADMIN").id();assertThat(grants.exists(target.id(),role,"ORG:"+school.id())).isTrue();
  assertThat(scopes.canAccess(target.id(),a.id())).isTrue();assertThat(scopes.canAccess(target.id(),b.id())).isTrue();assertThat(scopes.canAccess(target.id(),school.id())).isFalse();
  assertThatThrownBy(()->users.batchRemoveRole(new BatchAssignRoleToRoleCommand(operator.id(),role,List.of(new BatchRoleAssignmentItem(target.id(),school.id()))))).isInstanceOf(IllegalStateException.class);
  members.revokeMembers(operator.id(),a.id(),List.of(target.id()),true);
  assertThat(grants.exists(target.id(),role,"ORG:"+school.id())).isTrue();assertThat(scopes.canAccess(target.id(),a.id())).isFalse();assertThat(scopes.canAccess(target.id(),b.id())).isTrue();
  Organization another=school("MULTI_OTHER");join(target,another);users.assignRole(new AssignRoleToUserCommand(target.id(),role,another.id()));
  members.revokeMembers(operator.id(),b.id(),List.of(target.id()),true);
  assertThat(grants.exists(target.id(),role,"ORG:"+school.id())).isFalse();assertThat(grants.exists(target.id(),role,"ORG:"+another.id())).isTrue();
 }
 @Test void cancellationPreservesPreexistingManualSchoolRole() {
  User operator=admin("manual_operator"),target=user("manual_target");Organization school=school("MANUAL"),campus=campus(school,"MANUAL_C",0);join(target,school);
  Long role=roles.findByCode("ORG_ADMIN").id();users.assignRole(new AssignRoleToUserCommand(target.id(),role,school.id()));
  members.addMembers(operator.id(),campus.id(),List.of(target.id()),true);members.revokeMembers(operator.id(),campus.id(),List.of(target.id()),true);
  assertThat(grants.exists(target.id(),role,"ORG:"+school.id())).isTrue();
 }
 @Test void campusScopeRejectsUnboundForeignAndOutOfScopeMutations() {
  User operator=admin("limited_operator"),manager=user("limited_manager"),unbound=user("limited_unbound");Organization school=school("LIMITED"),a=campus(school,"LIMITED_A",0),b=campus(school,"LIMITED_B",1);
  members.addMembers(operator.id(),a.id(),List.of(manager.id()),true);
  assertThatThrownBy(()->members.addMembers(manager.id(),a.id(),List.of(unbound.id()),false)).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThatThrownBy(()->members.picker(manager.id(),a.id(),null,null,false,"CANDIDATES",null,"UNBOUND",null,1,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThatThrownBy(()->members.addMembers(manager.id(),b.id(),List.of(unbound.id()),false)).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThatThrownBy(()->users.assignAuthorizedRole(new AssignRoleToUserCommand(unbound.id(),roles.findByCode("SYS_ADMIN").id(),null,manager.id()))).isInstanceOf(SystemOperationAccessDeniedException.class);
 }
 @Test void allScopeCustomPermissionsStillOnlyListCurrentSchoolUsers() {
  User operator=user("custom_all_operator"),target=user("custom_unbound_target");Organization school=school("CUSTOM_ALL_SCHOOL");
  var role=com.lingdong.learning.iam.domain.Role.custom(90008101L,"CUSTOM_ALL_MEMBER_MANAGER","Manager",null,com.lingdong.learning.iam.domain.RoleDataScope.ALL);roles.insert(role);
  users.assignRole(new AssignRoleToUserCommand(operator.id(),role.id(),null));
  long permissionId=90008110L;
  for(String code:List.of("IAM_USER_LIST","IAM_USER_ORGANIZATION_ASSIGN","IAM_DATA_SCOPE_CONFIGURE"))jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'ALLOW')",permissionId++,operator.id(),permissions.findByCode(code).id());
  assertThatThrownBy(()->members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",null,"UNBOUND",null,1,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThat(members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",null,"SCHOOL",null,1,20).items()).isEmpty();
  Organization campus=campus(school,"CUSTOM_ALL_CAMPUS",0);join(target,campus);
  var picker=members.picker(operator.id(),school.id(),null,null,false,"CANDIDATES",null,"SCHOOL",null,1,20);
  assertThat(picker.allowedUnbound()).isFalse();assertThat(picker.items()).extracting(m->m.user().id()).containsExactly(target.id());
  assertThat(grants.hasRoleCode(operator.id(),"SYS_ADMIN")).isFalse();
 }
 @Test void newAccountDefaultsAreMinimalAndTeacherIdentityHasNoSchoolDataScope() {
  User operator=admin("defaults_operator");Organization school=school("DEFAULTS"),campus=campus(school,"DEFAULTS_C",0);
  for(UserType type:List.of(UserType.PLATFORM,UserType.ORGANIZATION,UserType.FAMILY)) {
   Long contextId=type==UserType.PLATFORM?school.parentId():type==UserType.FAMILY?organizations.createOrganization(new CreateOrganizationCommand("DEFAULTS_FAMILY","家庭","FAMILY",null,0)).id():campus.id();
   var result=users.createManagedUserResult(new CreateUserCommand("default_"+type,"Default",null,type,operator.id()),contextId,"Password123",null,null,null);
   String role=type==UserType.PLATFORM?"PLATFORM_USER":type==UserType.FAMILY?"PARENT":"ORG_MEMBER";
   assertThat(grants.hasRoleCode(result.user().id(),role)).isTrue();assertThat(grants.hasRoleCode(result.user().id(),"SYS_ADMIN")).isFalse();assertThat(grants.hasRoleCode(result.user().id(),"ORG_ADMIN")).isFalse();
   assertThat(scopes.resolve(result.user().id()).rootPaths()).isEmpty();
  }
  jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'ALLOW')",90008001L,operator.id(),permissions.findByCode("TEACHER_CREATE").id());
  var teacher=users.createManagedUserResult(new CreateUserCommand("default_teacher","Teacher",null,UserType.ORGANIZATION,operator.id()),campus.id(),"Password123",null,null,"TEACHER").user();
  assertThat(memberships.exists(teacher.id(),school.id())).isTrue();assertThat(grants.exists(teacher.id(),roles.findByCode("TEACHER").id(),"ORG:"+school.id())).isTrue();assertThat(scopes.resolve(teacher.id()).rootPaths()).isEmpty();
  jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'ALLOW')",90008002L,operator.id(),permissions.findByCode("TEACHER_READ").id());
  var actor=new com.lingdong.learning.auth.application.AuthenticatedUser(operator.id(),1L,operator.username(),operator.displayName(),com.lingdong.learning.auth.domain.AuthClientType.WEB,List.of("SYS_ADMIN"));
  assertThat(teachers.list(actor,new com.lingdong.learning.teacher.application.TeacherQuery("default_teacher",school.id(),null,null,1,20)).items()).extracting(t->t.id()).contains(teacher.id());
  assertThatThrownBy(()->users.createManagedUserResult(new CreateUserCommand("bad_default","Bad",null,UserType.ORGANIZATION,operator.id()),campus.id(),"Password123",null,null,"SYS_ADMIN")).isInstanceOf(IllegalArgumentException.class);
  assertThat(jdbc.queryForObject("select count(*) from sys_user where username='bad_default'",Integer.class)).isZero();
 }
 @Test void authorizedSchoolRoleUsesDescendantMembershipWithoutExpandingUserScope() {
  User operator=admin("descendant_operator"),target=user("descendant_grant_member"),foreign=user("descendant_grant_foreign");
  Organization school=school("DESCENDANT"),campus=campus(school,"DESCENDANT_C",0);
  Organization grade=organizations.createOrganization(new CreateOrganizationCommand("DESCENDANT_G","Grade","GRADE",campus.id(),0));
  Organization classroom=organizations.createOrganization(new CreateOrganizationCommand("DESCENDANT_CLASS","Class","CLASS",grade.id(),0));
  Organization otherSchool=school("DESCENDANT_OTHER");join(target,classroom);join(foreign,otherSchool);
  var role=com.lingdong.learning.iam.domain.Role.custom(90008201L,"DESCENDANT_SCHOOL_ROLE","School role",null,com.lingdong.learning.iam.domain.RoleDataScope.SCHOOL);roles.insert(role);
  var candidates=members.picker(operator.id(),null,role.id(),school.id(),false,"CANDIDATES",null,null,"descendant_grant_",1,20);
  assertThat(candidates.total()).isEqualTo(1);assertThat(candidates.items()).extracting(m->m.user().id()).containsExactly(target.id());
  assertThatThrownBy(()->users.batchAssignRole(new BatchAssignRoleToRoleCommand(operator.id(),role.id(),List.of(new BatchRoleAssignmentItem(target.id(),school.id()),new BatchRoleAssignmentItem(foreign.id(),school.id()))))).isInstanceOf(BatchRoleAssignmentException.class);
  assertThat(grants.exists(target.id(),role.id(),"ORG:"+school.id())).isFalse();
  users.batchAssignRole(new BatchAssignRoleToRoleCommand(operator.id(),role.id(),List.of(new BatchRoleAssignmentItem(target.id(),school.id()))));
  assertThat(grants.exists(target.id(),role.id(),"ORG:"+school.id())).isTrue();assertThat(memberships.exists(target.id(),school.id())).isFalse();
  assertThat(scopes.canAccess(target.id(),classroom.id())).isTrue();assertThat(scopes.canAccess(target.id(),school.id())).isFalse();assertThat(scopes.canAccess(target.id(),otherSchool.id())).isFalse();
  assertThatThrownBy(()->users.assignAuthorizedRole(new AssignRoleToUserCommand(foreign.id(),role.id(),school.id(),operator.id()))).isInstanceOf(IllegalStateException.class);
 }
 @Test void exactRoleRemovalPreservesOtherOrganizationBindingAndInvalidBatchIsAtomic() {
  User operator=admin("role_operator"),target=user("role_target");Organization one=school("ROLE_ONE"),two=school("ROLE_TWO");join(target,one);join(target,two);Long role=roles.findByCode("PARENT").id();
  users.assignRole(new AssignRoleToUserCommand(target.id(),role,one.id()));users.assignRole(new AssignRoleToUserCommand(target.id(),role,two.id()));
  var linked=members.picker(operator.id(),null,role,null,false,"LINKED",null,null,"role_target",1,1);
  assertThat(linked.total()).isEqualTo(2);assertThat(linked.items()).hasSize(1);assertThat(linked.items().get(0).assignments()).hasSize(1);
  var filtered=members.picker(operator.id(),null,role,null,false,"LINKED",one.id(),null,"role_target",1,20);
  assertThat(filtered.total()).isEqualTo(1);assertThat(filtered.items().get(0).assignments()).containsExactly(new RoleUserAssignment(target.id(),one.id()));
  User narrow=user("role_narrow");join(narrow,one);users.assignRole(new AssignRoleToUserCommand(narrow.id(),roles.findByCode("ORG_ADMIN").id(),one.id()));
  jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'ALLOW')",90008002L,narrow.id(),permissions.findByCode("IAM_USER_ROLE_ASSIGN").id());
  var scoped=members.picker(narrow.id(),null,role,null,false,"LINKED",null,null,"role_target",1,20);
  assertThat(scoped.total()).isEqualTo(1);assertThat(scoped.items().get(0).assignments()).containsExactly(new RoleUserAssignment(target.id(),one.id()));
  users.batchAssignRole(new BatchAssignRoleToRoleCommand(narrow.id(),role,List.of(new BatchRoleAssignmentItem(target.id(),null))));
  var selfGlobal=members.picker(narrow.id(),null,role,null,false,"LINKED",one.id(),null,"role_target",1,20);
  assertThat(selfGlobal.total()).isEqualTo(2);assertThat(selfGlobal.items().stream().flatMap(i->i.assignments().stream())).containsExactlyInAnyOrder(new RoleUserAssignment(target.id(),one.id()),new RoleUserAssignment(target.id(),null));
  User outside=user("role_outside");join(outside,two);
  assertThatThrownBy(()->users.batchAssignRole(new BatchAssignRoleToRoleCommand(narrow.id(),role,List.of(new BatchRoleAssignmentItem(outside.id(),null))))).isInstanceOf(BatchRoleAssignmentException.class);


  assertThatThrownBy(()->users.batchRemoveRole(new BatchAssignRoleToRoleCommand(operator.id(),role,List.of(new BatchRoleAssignmentItem(target.id(),one.id()),new BatchRoleAssignmentItem(operator.id(),two.id()))))).isInstanceOf(IllegalStateException.class);
  assertThat(grants.exists(target.id(),role,"ORG:"+one.id())).isTrue();
  users.batchRemoveRole(new BatchAssignRoleToRoleCommand(operator.id(),role,List.of(new BatchRoleAssignmentItem(target.id(),one.id()))));
  assertThat(grants.exists(target.id(),role,"ORG:"+one.id())).isFalse();assertThat(grants.exists(target.id(),role,"ORG:"+two.id())).isTrue();
 }
 @Test void relationshipClassOptionsExposeAndFilterActualSchool() {
  User operator=admin("school_options_admin");Organization a=school("OPTIONS_A"),b=school("OPTIONS_B");
  Organization ga=organizations.createOrganization(new CreateOrganizationCommand("OPTIONS_GA","年级","GRADE",a.id(),0));
  Organization gb=organizations.createOrganization(new CreateOrganizationCommand("OPTIONS_GB","年级","GRADE",b.id(),0));
  Organization ca=organizations.createOrganization(new CreateOrganizationCommand("OPTIONS_CA","本校班","CLASS",ga.id(),0));
  organizations.createOrganization(new CreateOrganizationCommand("OPTIONS_CB","外校班","CLASS",gb.id(),0));
  var actor=new com.lingdong.learning.auth.application.AuthenticatedUser(operator.id(),1L,operator.username(),operator.displayName(),com.lingdong.learning.auth.domain.AuthClientType.WEB,List.of("SYS_ADMIN"));
  assertThat(studentLifecycle.listClassOptions(actor,a.id())).extracting(c->c.id()).containsExactly(ca.id());
  assertThat(studentLifecycle.listClassOptions(actor,a.id())).allSatisfy(c->assertThat(c.schoolOrganizationId()).isEqualTo(a.id()));
 }

 @Test void teacherCreatedInClassIsAutomaticallyAssignedAndAssignmentDenialCreatesNoAccount() {
  User operator=admin("class_teacher_creator");Organization school=school("CREATE_CLASS_TEACHER");
  Organization grade=organizations.createOrganization(new CreateOrganizationCommand("CREATE_CLASS_TG","年级","GRADE",school.id(),0));
  Organization classroom=organizations.createOrganization(new CreateOrganizationCommand("CREATE_CLASS_TC","班级","CLASS",grade.id(),0));
  long permissionRow=90008500L;
  for(String code:List.of("TEACHER_CREATE","TEACHER_CLASS_ASSIGN"))jdbc.update("insert into sys_user_permission(id,user_id,permission_id,effect) values(?,?,?,'ALLOW')",permissionRow++,operator.id(),permissions.findByCode(code).id());
  User created=users.createManagedUserResult(new CreateUserCommand("auto_class_teacher","班级教师",null,UserType.ORGANIZATION,operator.id()),classroom.id(),"Password123",null,null,"TEACHER").user();
  assertThat(jdbc.queryForObject("select count(*) from edu_teacher_class where teacher_user_id=? and class_organization_id=? and status='ACTIVE'",Integer.class,created.id(),classroom.id())).isEqualTo(1);
  assertThat(jdbc.queryForObject("select count(*) from edu_teacher_class_change_log where teacher_user_id=? and class_organization_id=?",Integer.class,created.id(),classroom.id())).isEqualTo(1);
  jdbc.update("update sys_user_permission set effect='DENY' where user_id=? and permission_id=?",operator.id(),permissions.findByCode("TEACHER_CLASS_ASSIGN").id());
  assertThatThrownBy(()->users.createManagedUserResult(new CreateUserCommand("denied_class_teacher","不应创建",null,UserType.ORGANIZATION,operator.id()),classroom.id(),"Password123",null,null,"TEACHER")).isInstanceOf(SystemOperationAccessDeniedException.class);
  assertThat(jdbc.queryForObject("select count(*) from sys_user where username='denied_class_teacher'",Integer.class)).isZero();
 }

}
