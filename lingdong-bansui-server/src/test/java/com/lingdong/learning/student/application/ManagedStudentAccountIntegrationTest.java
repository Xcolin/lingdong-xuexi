package com.lingdong.learning.student.application;

import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.StudentCredentialMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentOrganizationMapper;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ManagedStudentAccountIntegrationTest {
    @Autowired UserAccessApplicationService access;
    @Autowired InstitutionEnrollmentService enrollment;
    @Autowired com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskOptionMapper taskOptions;
    @Autowired com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper memberships;
    @Autowired UserMapper users;
    @Autowired StudentMapper students;
    @Autowired StudentCredentialMapper credentials;
    @Autowired StudentOrganizationMapper relationships;
    @Autowired OrganizationMapper organizations;
    @Autowired JdbcTemplate jdbc;
    @Autowired StudentManagementAccessService management;
    @Autowired StudentOrganizationLifecycleService lifecycle;
    @MockitoBean PermissionDecisionService permissions;
    @MockitoBean OrganizationDataScopeService scopes;
    @BeforeEach void setup() {
        when(permissions.isAllowed(eq(1L), eq(PermissionClient.WEB), anyString())).thenReturn(true);
        when(scopes.canAccess(eq(1L), anyLong())).thenReturn(true);
        when(scopes.resolve(1L)).thenReturn(com.lingdong.learning.datascope.application.OrganizationDataScope.all(false));
        if (organizations.findById(900040L) == null) {
            organizations.insert(Organization.create(900040L, null, "ROOT", "UNIFIED_SCHOOL", "统一学校", "SCHOOL", "/UNIFIED_SCHOOL/", 1, null));
            organizations.insert(Organization.create(900041L, 900040L, "PARENT:900040", "UNIFIED_GRADE", "三年级", "GRADE", "/UNIFIED_SCHOOL/UNIFIED_GRADE/", 1, null));
            organizations.insert(Organization.create(900042L, 900041L, "PARENT:900041", "UNIFIED_CLASS", "一班", "CLASS", "/UNIFIED_SCHOOL/UNIFIED_GRADE/UNIFIED_CLASS/", 1, null));
        }
    }
    @Test @Transactional void createsUnifiedAccountAndRepairIsIdempotent() {
        var user = access.createManagedUserInOrganization(new CreateUserCommand("xiang_unified", "向同学", null, UserType.STUDENT, 1L), 900042L, null, null, null);
        var student = students.findByStudentUserId(user.id());
        assertThat(student.studentUserId()).isEqualTo(user.id());
        assertThat(student.gradeCode()).isEqualTo("三年级");
        assertThat(user.username()).isEqualTo("xiang_unified");
        assertThat(user.passwordHash()).isNull();
        assertThat(relationships.findActiveEnrollmentOrganizationIds(student.id())).containsExactly(900040L);
        assertThat(relationships.findActiveClassOrganizationIds(student.id())).containsExactly(900042L);
        assertThat(jdbc.queryForObject("select count(*) from growth_point_account where student_id=?", Integer.class, student.id())).isEqualTo(1);
        String hash = credentials.findByStudentUserId(user.id()).codeHash();
        access.completeStudentProfile(1L, user.id(), 900042L, null, "不得覆盖");
        assertThat(students.findByStudentUserId(user.id()).id()).isEqualTo(student.id());
        assertThat(students.findByStudentUserId(user.id()).gradeCode()).isEqualTo("三年级");
        assertThat(credentials.findByStudentUserId(user.id()).codeHash()).isEqualTo(hash);
    }
    @Test void rollsBackAllWritesWhenStudentIdentityCannotBeGranted() {
        String oldStatus = jdbc.queryForObject("select status from sys_role where role_code='STUDENT'", String.class);
        jdbc.update("update sys_role set status='DISABLED' where role_code='STUDENT'");
        try {
            assertThatThrownBy(() -> access.createManagedUserInOrganization(new CreateUserCommand("rollback_unified", "回滚学生", null, UserType.STUDENT, 1L), 900042L, null, null, null))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(users.findByUsername("rollback_unified")).isNull();
            assertThat(jdbc.queryForObject("select count(*) from edu_student where student_name='回滚学生'", Integer.class)).isZero();
        } finally { jdbc.update("update sys_role set status=? where role_code='STUDENT'", oldStatus); }
    }

    @Test @Transactional void narrowClassScopeSeesCreatedStudentAndLosesVisibilityAfterClassTransfer() {
        var user = access.createManagedUserInOrganization(new CreateUserCommand("scope_class_unified", "班级范围学生", null, UserType.STUDENT, 1L), 900042L, null, null, null);
        var student = students.findByStudentUserId(user.id());
        var scope = new com.lingdong.learning.datascope.application.OrganizationDataScope(false, false, java.util.List.of("/UNIFIED_SCHOOL/UNIFIED_GRADE/UNIFIED_CLASS/"));
        when(scopes.resolve(1L)).thenReturn(scope);
        var operator = mock(com.lingdong.learning.auth.application.AuthenticatedUser.class);
        when(operator.userId()).thenReturn(1L);
        assertThat(management.studentAllowed(operator, student.id())).isTrue();
        assertThat(lifecycle.find(operator, student.id()).studentId()).isEqualTo(student.id());
        assertThat(relationships.findActiveSummariesByOrganizationAdministratorScope(scope)).extracting(r -> r.studentId()).contains(student.id());
        assertThat(relationships.existsActiveEnrollmentInScope(student.id(), 900040L, scope)).isTrue();
        assertThat(relationships.findClassStudentOptions(900042L, scope, false)).extracting(r -> r.id()).contains(student.id());
        var query = new StudentDirectoryQuery(null, 1L, false, false, true, 0, 100, true, false, scope.rootPaths());
        assertThat(students.findPage(query)).extracting(Student::id).contains(student.id());
        relationships.deactivateActiveClasses(student.id());
        organizations.insert(Organization.create(900043L, 900041L, "PARENT:900041", "UNIFIEDXCLASS", "二班", "CLASS", "/UNIFIED_SCHOOL/UNIFIED_GRADE/UNIFIEDXCLASS/", 1, null));
        relationships.insertClass(900044L, student.id(), 900043L);
        assertThat(relationships.findActiveSummariesByOrganizationAdministratorScope(scope)).extracting(r -> r.studentId()).doesNotContain(student.id());
        assertThat(management.studentAllowed(operator, student.id())).isFalse();
        assertThatThrownBy(() -> lifecycle.find(operator, student.id())).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
        assertThat(relationships.existsActiveEnrollmentInScope(student.id(), 900040L, scope)).isFalse();
        assertThat(students.findPage(query)).extracting(Student::id).doesNotContain(student.id());
        assertThat(relationships.findClassStudentOptions(900042L, scope, false)).extracting(r -> r.id()).doesNotContain(student.id());
    }

    @Test @Transactional void narrowGradeScopeSeesOnlyItsPendingGradeMembershipWithActiveSchoolEnrollment() {
        var user = access.createManagedUserInOrganization(new CreateUserCommand("scope_grade_unified", "年级待分班学生", null, UserType.STUDENT, 1L), 900041L, null, null, null);
        var student = students.findByStudentUserId(user.id());
        var scope = new com.lingdong.learning.datascope.application.OrganizationDataScope(false, false, java.util.List.of("/UNIFIED_SCHOOL/UNIFIED_GRADE/"));
        assertThat(relationships.existsActiveEnrollmentInScope(student.id(), 900040L, scope)).isTrue();
        assertThat(relationships.findClassStudentOptions(900042L, scope, false)).extracting(r -> r.id()).contains(student.id());
        var schoolOnly = access.createManagedUserInOrganization(new CreateUserCommand("scope_school_unified", "同校其他待分班", null, UserType.STUDENT, 1L), 900040L, null, null, "三年级");
        var schoolStudent = students.findByStudentUserId(schoolOnly.id());
        assertThat(relationships.existsActiveEnrollmentInScope(schoolStudent.id(), 900040L, scope)).isFalse();
        assertThat(relationships.findActiveSummariesByOrganizationAdministratorScope(scope)).extracting(r -> r.studentId()).contains(student.id()).doesNotContain(schoolStudent.id());
    }
    @Test @Transactional void familyStudentJoinsSchoolAndTwoTrainingCoursesWithOneIdentity() {
        if(users.findById(1L)==null)users.insert(com.lingdong.learning.user.domain.User.create(1L,"enrollment_operator","申请管理者",null,UserType.PLATFORM));
        organizations.insert(Organization.create(901000L,null,"ROOT","MULTI_FAMILY","多机构验证家庭","FAMILY","/MULTI_FAMILY/",1,null));
        organizations.insert(Organization.create(901010L,null,"ROOT","MULTI_TRAINING","培训机构","TRAINING_INSTITUTION","/MULTI_TRAINING/",1,null));
        organizations.insert(Organization.create(901011L,901010L,"PARENT:901010","MULTI_COURSE1","钢琴课","CLASS","/MULTI_TRAINING/MULTI_COURSE1/",1,null));
        organizations.insert(Organization.create(901012L,901010L,"PARENT:901010","MULTI_COURSE2","舞蹈课","CLASS","/MULTI_TRAINING/MULTI_COURSE2/",1,null));
        var created=access.createManagedUserResult(new CreateUserCommand("multi_family_student","家庭学生",null,UserType.STUDENT,1L),901000L,null,null,null);
        var student=students.findByStudentUserId(created.user().id());
        assertThat(student.gradeCode()).isNull();
        assertThat(relationships.findActiveEnrollmentOrganizationIds(student.id())).isEmpty();
        assertThat(created.initialLoginCode()).isNotBlank();
        var credential=credentials.findByStudentUserId(created.user().id());
        var actor=new com.lingdong.learning.auth.application.AuthenticatedUser(1L,null,"manager","管理者",com.lingdong.learning.auth.domain.AuthClientType.WEB,java.util.List.of());
        for(Long classId:java.util.List.of(900042L,901011L,901012L)) {
            var invite=enrollment.createInvitation(actor,classId,7);
            assertThat(enrollment.resolve(actor,invite.inviteCode()).classOrganizationId()).isEqualTo(classId);
            var request=enrollment.request(actor,created.user().id(),invite.inviteCode(),901000L);
            assertThat(request.studentUserId()).isEqualTo(created.user().id());
            assertThat(enrollment.review(actor,request.id(),true,null).status()).isEqualTo("APPROVED");
        }
        assertThat(relationships.findActiveEnrollmentOrganizationIds(student.id())).containsExactlyInAnyOrder(900040L,901010L);
        assertThat(relationships.findActiveClassOrganizationIds(student.id())).containsExactlyInAnyOrder(900042L,901011L,901012L);
        assertThat(credentials.findByStudentUserId(created.user().id()).codeHash()).isEqualTo(credential.codeHash());
        assertThat(memberships.findOrganizationIds(created.user().id())).contains(901000L,900040L,900042L,901010L,901011L,901012L);
        assertThat(lifecycle.find(actor,student.id(),901010L).currentClassOrganizationId()).isNull();
        assertThat(lifecycle.find(actor,student.id(),901010L).currentClassOrganizationIds()).containsExactlyInAnyOrder(901011L,901012L);
        assertThatThrownBy(()->lifecycle.find(actor,student.id())).hasMessageContaining("institutionOrganizationId");
        assertThat(relationships.findClassStudentOptions(900042L,com.lingdong.learning.datascope.application.OrganizationDataScope.all(false),true))
            .filteredOn(o->o.id().equals(student.id())).extracting(o->o.currentClassId()).containsExactly(900042L);
        var trainingSummary=lifecycle.list(actor).stream().filter(row->row.studentId().equals(student.id())&&row.enrollmentOrganizationId().equals(901010L)).toList();
        assertThat(trainingSummary).hasSize(1);
        assertThat(trainingSummary.get(0).institutionTypeCode()).isEqualTo("TRAINING_INSTITUTION");
        assertThat(trainingSummary.get(0).currentClassOrganizationIds()).containsExactlyInAnyOrder(901011L,901012L);
        var scopedSchoolOptions=taskOptions.findScopedStudentOptions(java.util.List.of(900040L,900042L,901010L,901011L,901012L),900042L,null);
        assertThat(scopedSchoolOptions).filteredOn(row->row.id().equals(student.id())).extracting(row->row.currentClassId()).containsExactly(900042L);
        var scopedTrainingOptions=taskOptions.findScopedStudentOptions(java.util.List.of(900040L,900042L,901010L,901011L,901012L),901010L,null);
        assertThat(scopedTrainingOptions).filteredOn(row->row.id().equals(student.id())).extracting(row->row.currentClassId()).containsExactly((Long)null);
        organizations.insert(Organization.create(901013L,900041L,"PARENT:900041","MULTI_SCHOOL_CLASS2","学校二班","CLASS","/UNIFIED_SCHOOL/UNIFIED_GRADE/MULTI_SCHOOL_CLASS2/",1,null));
        lifecycle.transferClass(actor,student.id(),new TransferStudentClassCommand(901013L,"同校转班"));
        assertThat(relationships.findActiveClassOrganizationIds(student.id())).containsExactlyInAnyOrder(901013L,901011L,901012L);
        lifecycle.deactivateClass(actor,student.id(),901011L,"退出钢琴");
        assertThat(relationships.findActiveClassOrganizationIds(student.id())).containsExactlyInAnyOrder(901013L,901012L);
        lifecycle.deactivate(actor,student.id(),new DeactivateStudentOrganizationCommand(901010L,"退出培训机构"));
        assertThat(relationships.findActiveClassOrganizationIds(student.id())).containsExactly(901013L);
        assertThat(relationships.findActiveEnrollmentOrganizationIds(student.id())).containsExactly(900040L);
        assertThat(memberships.findOrganizationIds(created.user().id())).contains(901000L,900040L,901013L).doesNotContain(900042L,901010L,901011L,901012L);
    }
}




