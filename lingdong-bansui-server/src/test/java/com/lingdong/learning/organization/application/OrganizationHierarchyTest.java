package com.lingdong.learning.organization.application;
import com.lingdong.learning.organization.domain.Organization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test")
// 事务化回滚：本类原本非事务提交，会向共享测试库遗留组织/用户/学生数据（如 88008001），污染 StudentManagementControllerTest 等事务性测试的目录断言
@Transactional
class OrganizationHierarchyTest {
 @Autowired OrganizationApplicationService service;
 @Autowired OrganizationChangeApplicationService changes;
 @Autowired com.lingdong.learning.user.application.UserAccessApplicationService accounts;
 @Autowired com.lingdong.learning.iam.infrastructure.persistence.RoleMapper roles;
 @Test void rejectsInvalidMoveAndCrossSchoolDirectMove() {
  var u=accounts.createUser(new com.lingdong.learning.user.application.CreateUserCommand("hier_admin", "层级管理员", null, com.lingdong.learning.user.domain.UserType.PLATFORM));
  accounts.assignRole(new com.lingdong.learning.user.application.AssignRoleToUserCommand(u.id(),roles.findByCode("SYS_ADMIN").id(),null));
  var school=service.createOrganization(new CreateOrganizationCommand("HIER_MOVE_SCHOOL","移动学校","SCHOOL",null,0));
  var other=service.createOrganization(new CreateOrganizationCommand("HIER_MOVE_OTHER","另一学校","SCHOOL",null,0));
  var grade=service.createOrganization(new CreateOrganizationCommand("HIER_MOVE_GRADE","移动年级","GRADE",school.id(),0));
  var otherGrade=service.createOrganization(new CreateOrganizationCommand("HIER_MOVE_OTHER_GRADE","其他年级","GRADE",other.id(),0));
  var cls=service.createOrganization(new CreateOrganizationCommand("HIER_MOVE_CLASS","移动班级","CLASS",grade.id(),0));
  assertThatThrownBy(() -> changes.directMove(new MoveOrganizationCommand(u.id(),cls.id(),school.id(),cls.versionNo()))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> changes.directMove(new MoveOrganizationCommand(u.id(),grade.id(),otherGrade.id(),grade.versionNo()))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> changes.directMove(new MoveOrganizationCommand(u.id(),cls.id(),otherGrade.id(),cls.versionNo()))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void rejectsSchoolDirectClassAndRootGrade() {
  Organization school=service.createOrganization(new CreateOrganizationCommand("HIER_SCHOOL", "层级学校", "SCHOOL", null, 0));
  assertThatThrownBy(() -> service.createOrganization(new CreateOrganizationCommand("HIER_BAD_CLASS", "错误班级", "CLASS", school.id(), 0))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> service.createOrganization(new CreateOrganizationCommand("HIER_BAD_GRADE", "错误年级", "GRADE", null, 0))).isInstanceOf(IllegalArgumentException.class);
  Organization grade=service.createOrganization(new CreateOrganizationCommand("HIER_GRADE", "一年级", "GRADE", school.id(), 0));
  Organization classroom=service.createOrganization(new CreateOrganizationCommand("HIER_CLASS", "一班", "CLASS", grade.id(), 0));
  assertThat(classroom.path()).startsWith(grade.path());
  assertThatThrownBy(() -> service.createOrganization(new CreateOrganizationCommand("HIER_SCHOOL_GRADE", "非法学校", "SCHOOL", grade.id(), 0))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> service.createOrganization(new CreateOrganizationCommand("HIER_CHILD_CLASS", "非法子节点", "REGION", classroom.id(), 0))).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> service.createOrganization(new CreateOrganizationCommand("HIER_GRADE_REGION", "非法年级子节点", "REGION", grade.id(), 0))).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void rejectsIndirectNestedSchoolsAndMovingSchoolSubtreeInsideSchool() {
  var u=accounts.createUser(new com.lingdong.learning.user.application.CreateUserCommand("hier_nested_admin","学校层级管理员",null,com.lingdong.learning.user.domain.UserType.PLATFORM));
  accounts.assignRole(new com.lingdong.learning.user.application.AssignRoleToUserCommand(u.id(),roles.findByCode("SYS_ADMIN").id(),null));
  var school=service.createOrganization(new CreateOrganizationCommand("HIER_NEST_SCHOOL","祖先学校","SCHOOL",null,0));
  var department=service.createOrganization(new CreateOrganizationCommand("HIER_NEST_DEPT","学校部门","REGION",school.id(),0));
  assertThatThrownBy(() -> service.createOrganization(new CreateOrganizationCommand("HIER_NEST_BAD_SCHOOL","嵌套学校","SCHOOL",department.id(),0))).isInstanceOf(IllegalArgumentException.class);
  var root=service.createOrganization(new CreateOrganizationCommand("HIER_NEST_REGION","带学校区域","REGION",null,0));
  service.createOrganization(new CreateOrganizationCommand("HIER_NEST_SUB_SCHOOL","区域学校","SCHOOL",root.id(),0));
  assertThatThrownBy(() -> changes.directMove(new MoveOrganizationCommand(u.id(),root.id(),department.id(),root.versionNo()))).isInstanceOf(IllegalArgumentException.class);
 }

 @Test void supportsMixedSchoolAndCampusGradesButRejectsCrossSchoolCampusMove() {
  if (types.findByCode("CAMPUS")==null) service.createOrganizationType(new CreateOrganizationTypeCommand("CAMPUS","校区",5));
  var u=accounts.createUser(new com.lingdong.learning.user.application.CreateUserCommand("hier_campus_admin","校区管理员",null,com.lingdong.learning.user.domain.UserType.PLATFORM));
  accounts.assignRole(new com.lingdong.learning.user.application.AssignRoleToUserCommand(u.id(),roles.findByCode("SYS_ADMIN").id(),null));
  var school=service.createOrganization(new CreateOrganizationCommand("HIER_CMP_SCHOOL","校区学校","SCHOOL",null,0));
  var other=service.createOrganization(new CreateOrganizationCommand("HIER_CMP_OTHER","其他校区学校","SCHOOL",null,0));
  var campus=service.createOrganization(new CreateOrganizationCommand("HIER_CMP_CAMPUS","第一校区","CAMPUS",school.id(),0));
  var grade=service.createOrganization(new CreateOrganizationCommand("HIER_CMP_GRADE","校区年级","GRADE",campus.id(),0));
  var direct=service.createOrganization(new CreateOrganizationCommand("HIER_CMP_DIRECT","直属年级","GRADE",school.id(),1));
  var classroom=service.createOrganization(new CreateOrganizationCommand("HIER_CMP_CLASS","校区班级","CLASS",grade.id(),0));
  assertThat(classroom.path()).startsWith(campus.path());
  assertThat(OrganizationHierarchy.schoolOf(classroom,organizations).id()).isEqualTo(school.id());
  assertThatThrownBy(() -> changes.directMove(new MoveOrganizationCommand(u.id(),campus.id(),other.id(),campus.versionNo()))).isInstanceOf(IllegalArgumentException.class);
  var moved=changes.directMove(new MoveOrganizationCommand(u.id(),grade.id(),school.id(),grade.versionNo()));
  assertThat(moved.parentId()).isEqualTo(school.id());
  assertThat(organizations.findById(classroom.id()).path()).startsWith(moved.path());
 }
 @Autowired com.lingdong.learning.organization.infrastructure.persistence.OrganizationTypeMapper types;
 @Autowired com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper organizations;

 @Test void campusMembershipTeacherCanReachSiblingCampusButNotOtherSchool() {
  if(types.findByCode("CAMPUS")==null) service.createOrganizationType(new CreateOrganizationTypeCommand("CAMPUS","校区",5));
  var school=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_SCHOOL","教师校区学校","SCHOOL",null,0));
  var other=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_OTHER","教师其他学校","SCHOOL",null,0));
  var campus=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_CAMPUS","教师所属校区","CAMPUS",school.id(),0));
  var campus2=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_CAMPUS_TWO","教师目标校区","CAMPUS",school.id(),1));
  var grade=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_GRADE","教师年级","GRADE",campus2.id(),0));
  var otherGrade=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_OTHER_GRADE","教师其他年级","GRADE",other.id(),0));
  var cls=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_CLASS","教师目标班级","CLASS",grade.id(),0));
  var outside=service.createOrganization(new CreateOrganizationCommand("HIER_TCH_OTHER_CLASS","教师其他班级","CLASS",otherGrade.id(),0));
  var teacher=accounts.createUser(new com.lingdong.learning.user.application.CreateUserCommand("hier_campus_teacher","校区教师",null,com.lingdong.learning.user.domain.UserType.ORGANIZATION));
  accounts.associateWithOrganization(new com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand(teacher.id(),campus.id()));
  assertThat(teacherClasses.existsTeacherOrganizationInClassAncestors(teacher.id(),cls.id())).isTrue();
  assertThat(teacherClasses.existsTeacherOrganizationInClassAncestors(teacher.id(),outside.id())).isFalse();
 }
 @Autowired com.lingdong.learning.learningtask.infrastructure.persistence.TeacherClassMapper teacherClasses;

 @Test void pendingCampusStudentScopeUsesMembershipUntilActiveClassTakesPrecedence() {
  if(types.findByCode("CAMPUS")==null) service.createOrganizationType(new CreateOrganizationTypeCommand("CAMPUS","校区",5));
  var school=service.createOrganization(new CreateOrganizationCommand("HIER_STU_SCHOOL","学生校区学校","SCHOOL",null,0));
  var campus=service.createOrganization(new CreateOrganizationCommand("HIER_STU_CAMPUS","学生第一校区","CAMPUS",school.id(),0));
  var second=service.createOrganization(new CreateOrganizationCommand("HIER_STU_CAMPUS_TWO","学生第二校区","CAMPUS",school.id(),1));
  var grade=service.createOrganization(new CreateOrganizationCommand("HIER_STU_GRADE","第二校区年级","GRADE",second.id(),0));
  var cls=service.createOrganization(new CreateOrganizationCommand("HIER_STU_CLASS","第二校区班级","CLASS",grade.id(),0));
  var user=accounts.createUser(new com.lingdong.learning.user.application.CreateUserCommand("hier_campus_pending","未分班学生",null,com.lingdong.learning.user.domain.UserType.ORGANIZATION));
  accounts.associateWithOrganization(new com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand(user.id(),campus.id()));
  var student=com.lingdong.learning.student.domain.Student.create(88008001L,"未分班学生","一年级",user.id());
  studentRows.insert(student); studentRelations.insertEnrollment(88008002L,student.id(),school.id());
  var scope=new com.lingdong.learning.datascope.application.OrganizationDataScope(false,false,java.util.List.of(campus.path()));
  assertThat(studentRelations.existsActiveEnrollmentInScope(student.id(),school.id(),scope)).isTrue();
  assertThat(taskOptions.findScopedStudentOptions(java.util.List.of(campus.id()),campus.id(),null)).extracting(com.lingdong.learning.learningtask.infrastructure.persistence.StudentOptionRow::id).contains(student.id());
  studentRelations.insertClass(88008003L,student.id(),cls.id());
  assertThat(studentRelations.existsActiveEnrollmentInScope(student.id(),school.id(),scope)).isFalse();
  assertThat(taskOptions.findScopedStudentOptions(java.util.List.of(campus.id()),campus.id(),null)).extracting(com.lingdong.learning.learningtask.infrastructure.persistence.StudentOptionRow::id).doesNotContain(student.id());
 }
 @Autowired com.lingdong.learning.student.infrastructure.persistence.StudentMapper studentRows;
 @Autowired com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper studentRelations;
 @Autowired com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskOptionMapper taskOptions;
}
