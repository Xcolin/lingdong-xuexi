package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.dashboard.application.ActivityTrendService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.teacher.application.TeacherManagementAccessService;
import com.lingdong.learning.user.application.*;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomBusinessPermissionIntegrationTest {
    @Autowired StudentApplicationService students;
    @Autowired StudentOrganizationLifecycleService lifecycle;
    @Autowired ClassManagementApplicationService classes;
    @Autowired TeacherManagementAccessService teachers;
    @Autowired ActivityTrendService activity;
    @Autowired OrganizationApplicationService organizations;
    @Autowired UserAccessApplicationService users;
    @Autowired RoleMapper roles;
    @Autowired IdGenerator ids;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;

    @Test
    void customAllRoleUsesPermissionsAndAllOrganizationScopeWithoutAdminBindings() {
        var own = school("custom_all_own");
        var outside = school("custom_all_outside");
        var operator = operator("custom_all", RoleDataScope.ALL, null);
        long ownStudent = enrollment(own, "own");
        long outsideStudent = enrollment(outside, "outside");
        assertThat(operator.roleCodes()).doesNotContain("ORG_ADMIN", "SYS_ADMIN");
        assertThat(classes.listManageableSchools(operator)).extracting(Organization::id)
                .contains(own.id(), outside.id());
        assertThat(students.listStudents(operator, null, 1, 100).items())
                .extracting(com.lingdong.learning.student.domain.Student::id).contains(ownStudent, outsideStudent);
        assertThat(lifecycle.list(operator)).extracting(StudentOrganizationRelationshipSummary::studentId)
                .contains(ownStudent, outsideStudent);
        assertThat(teachers.requireManageableSchool(operator, outside.id()).id()).isEqualTo(outside.id());
        assertThat(activity.activityTrends(operator, null, null)).isNotNull();
        jdbc.update("INSERT INTO sys_user_permission(id, user_id, permission_id, effect) SELECT ?, ?, id, 'DENY' FROM sys_permission WHERE permission_code = 'CLASS_READ'", ids.nextId(), operator.userId());
        sqlSession.clearCache(); // Direct JDBC fixture mutations do not invalidate this transaction's MyBatis local cache.
        assertThatThrownBy(() -> classes.listClasses(operator)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    @Test
    void customSchoolRoleFiltersBeforePaginationAndDeniesOutsideObjectsAndMissingPermission() {
        var own = school("custom_school_own");
        var outside = school("custom_school_outside");
        var operator = operator("custom_school", RoleDataScope.SCHOOL, own);
        long ownStudent = enrollment(own, "own");
        long outsideStudent = enrollment(outside, "outside");
        var page = students.listStudents(operator, null, 1, 100);
        assertThat(page.items()).extracting(com.lingdong.learning.student.domain.Student::id).containsExactly(ownStudent);
        assertThat(page.total()).isEqualTo(1);
        assertThat(classes.listManageableSchools(operator)).extracting(Organization::id).containsExactly(own.id());
        assertThat(lifecycle.list(operator)).extracting(StudentOrganizationRelationshipSummary::studentId).containsExactly(ownStudent);
        assertThatThrownBy(() -> students.findStudent(operator, outsideStudent)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> teachers.requireManageableSchool(operator, outside.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> classes.createClass(operator, new CreateClassCommand(outside.id(), "outside", 1)))
                .isInstanceOf(ResourceNotFoundException.class);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id = (SELECT id FROM sys_role WHERE role_code = ?) AND permission_id = (SELECT id FROM sys_permission WHERE permission_code = 'STUDENT_READ')", "CUSTOM_BUSINESS_custom_school");
        sqlSession.clearCache();
        assertThatThrownBy(() -> students.listStudents(operator, null, 1, 10)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(() -> students.findStudent(operator, ownStudent)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }

    private Organization school(String code) {
        return organizations.createOrganization(new CreateOrganizationCommand(code.toUpperCase(java.util.Locale.ROOT), code, "SCHOOL", null, 1));
    }

    private AuthenticatedUser operator(String username, RoleDataScope scope, Organization school) {
        var user = users.createUser(new CreateUserCommand(username, username, null, UserType.ORGANIZATION));
        var role = Role.custom(ids.nextId(), "CUSTOM_BUSINESS_" + username, username, null, scope);
        roles.insert(role);
        if (school != null) users.associateWithOrganization(new AssociateUserWithOrganizationCommand(user.id(), school.id()));
        users.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), school == null ? null : school.id()));
        for (String permission : List.of("STUDENT_READ", "CLASS_READ", "CLASS_CREATE", "TEACHER_READ", "LEARNING_TASK_PROGRESS_READ", "STUDENT_ORGANIZATION_MANAGE")) {
            assertThat(jdbc.update("INSERT INTO sys_role_permission(id, role_id, permission_id) SELECT ?, ?, id FROM sys_permission WHERE permission_code = ?", ids.nextId(), role.id(), permission)).isEqualTo(1);
        }
        return new AuthenticatedUser(user.id(), ids.nextId(), username, username, AuthClientType.WEB, List.of(role.code()));
    }

    private long enrollment(Organization school, String name) {
        long student = ids.nextId();
        jdbc.update("INSERT INTO edu_student(id, student_name, status) VALUES (?, ?, 'ENABLED')", student, name);
        jdbc.update("INSERT INTO edu_student_organization(id, student_id, organization_id, relation_type, status) VALUES (?, ?, ?, 'ENROLLMENT', 'ACTIVE')", ids.nextId(), student, school.id());
        return student;
    }
}
