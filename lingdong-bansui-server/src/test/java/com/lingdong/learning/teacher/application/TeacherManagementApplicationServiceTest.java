package com.lingdong.learning.teacher.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TeacherManagementApplicationServiceTest {
    @Autowired private TeacherManagementApplicationService service;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private IdGenerator idGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void createsOrganizationTeacherWithSchoolRolePasswordAndClassesAtomically() {
        Fixture fixture = createFixture("teacher_manage_create");
        Organization firstClass = createClass(fixture.school(), "TEACHER_CREATE_CLASS_A", "教师创建一班");
        Organization secondClass = createClass(fixture.school(), "TEACHER_CREATE_CLASS_B", "教师创建二班");

        TeacherAccount created = service.create(fixture.currentUser(), new CreateTeacherCommand(
                "teacher_create_account", "创建教师", "13800138001", "Password123",
                fixture.school().id(), List.of(firstClass.id(), secondClass.id())
        ));

        assertThat(created.username()).isEqualTo("teacher_create_account");
        assertThat(created.displayName()).isEqualTo("创建教师");
        assertThat(created.schoolId()).isEqualTo(fixture.school().id());
        assertThat(created.classOrganizationIds()).containsExactly(firstClass.id(), secondClass.id());
        assertThat(Long.toString(created.id())).hasSize(19);
        assertThat(jdbcTemplate.queryForObject(
                "select user_type from sys_user where id = ?", String.class, created.id()))
                .isEqualTo("ORGANIZATION");
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from sys_user_role user_role
                join sys_role role on role.id = user_role.role_id
                where user_role.user_id = ? and role.role_code = 'TEACHER'
                """, Integer.class, created.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from sys_user_organization
                where user_id = ? and organization_id = ?
                """, Integer.class, created.id(), fixture.school().id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from edu_teacher_class
                where teacher_user_id = ? and status = 'ACTIVE'
                """, Integer.class, created.id())).isEqualTo(2);
        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from sys_user where id = ?", String.class, created.id());
        assertThat(passwordHash).doesNotContain("Password123");
        assertThat(passwordEncoder.matches("Password123", passwordHash)).isTrue();
    }

    @Test
    void rejectsClassOutsideSelectedSchool() {
        Fixture fixture = createFixture("teacher_manage_outside");
        Organization outsideClass = createClass(
                fixture.outsideSchool(), "TEACHER_OUTSIDE_CLASS", "范围外班级");

        assertThatThrownBy(() -> service.create(fixture.currentUser(), new CreateTeacherCommand(
                "teacher_outside_account", "跨校教师", null, "Password123",
                fixture.school().id(), List.of(outsideClass.id())
        ))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requiresClassAssignmentPermissionWhenCreatingTeacherWithInitialClasses() {
        Fixture fixture = createFixture("teacher_manage_class_permission");
        Organization classOrganization = createClass(
                fixture.school(), "TEACHER_PERMISSION_CLASS", "权限校验班级");
        Long permissionId = jdbcTemplate.queryForObject(
                "select id from sys_permission where permission_code = 'TEACHER_CLASS_ASSIGN'",
                Long.class);
        jdbcTemplate.update("""
                insert into sys_user_permission (id, user_id, permission_id, effect)
                values (?, ?, ?, 'DENY')
                """, idGenerator.nextId(), fixture.currentUser().userId(), permissionId);

        assertThatThrownBy(() -> service.create(fixture.currentUser(), new CreateTeacherCommand(
                "teacher_permission_account", "权限受限教师", null, "Password123",
                fixture.school().id(), List.of(classOrganization.id())
        ))).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys_user where username = 'teacher_permission_account'",
                Integer.class)).isZero();
    }

    @Test
    void listsOnlyTeachersInsideOrganizationScopeWithSchoolClassAndKeywordFilters() {
        Fixture fixture = createFixture("teacher_manage_list");
        Organization firstClass = createClass(
                fixture.school(), "TEACHER_LIST_CLASS_A", "目录筛选一班");
        Organization secondClass = createClass(
                fixture.school(), "TEACHER_LIST_CLASS_B", "目录筛选二班");
        TeacherAccount matched = service.create(fixture.currentUser(), new CreateTeacherCommand(
                "teacher_list_matched", "目录张老师", null, "Password123",
                fixture.school().id(), List.of(firstClass.id())
        ));
        service.create(fixture.currentUser(), new CreateTeacherCommand(
                "teacher_list_other", "目录李老师", null, "Password123",
                fixture.school().id(), List.of(secondClass.id())
        ));
        Fixture outsideFixture = createFixture("teacher_manage_list_external");
        service.create(outsideFixture.currentUser(), new CreateTeacherCommand(
                "teacher_list_external", "目录张老师", null, "Password123",
                outsideFixture.school().id(), List.of()
        ));

        TeacherPage result = service.list(fixture.currentUser(), new TeacherQuery(
                "张老师", fixture.school().id(), firstClass.id(), UserStatus.ENABLED, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).extracting(TeacherAccount::id).containsExactly(matched.id());
        assertThat(result.items().get(0).classOrganizationIds()).containsExactly(firstClass.id());
    }

    @Test
    void updatesTeacherProfileAndRecordsNonSensitiveAudit() {
        Fixture fixture = createFixture("teacher_manage_update");
        TeacherAccount teacher = createTeacher(fixture, "teacher_update_account", "修改前教师", "13800138011");

        TeacherAccount updated = service.updateProfile(fixture.currentUser(), teacher.id(),
                new UpdateTeacherProfileCommand("修改后教师", "13800138012"));

        assertThat(updated.displayName()).isEqualTo("修改后教师");
        assertThat(updated.mobile()).isEqualTo("13800138012");
        assertThat(updated.username()).isEqualTo("teacher_update_account");
        assertThat(updated.schoolId()).isEqualTo(fixture.school().id());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from sys_iam_change_audit
                where target_id = ? and event_type = 'USER_PROFILE_CHANGE'
                """, Integer.class, teacher.id())).isEqualTo(1);
    }

    @Test
    void resetsPasswordAndRevokesAllActiveTeacherSessions() {
        Fixture fixture = createFixture("teacher_manage_password");
        TeacherAccount teacher = createTeacher(fixture, "teacher_password_account", "密码教师", null);
        jdbcTemplate.update("""
                insert into auth_device_session (
                    id, user_id, client_type, device_id, device_name,
                    access_token_hash, refresh_token_hash, access_expires_at,
                    refresh_expires_at, status, last_active_at
                ) values (?, ?, 'WEB', ?, '教师电脑', ?, ?, ?, ?, 'ACTIVE', ?)
                """, idGenerator.nextId(), teacher.id(), "teacher-password-device",
                "a".repeat(64), "b".repeat(64), LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusDays(1), LocalDateTime.now());

        service.resetPassword(fixture.currentUser(), teacher.id(), "NewPassword123");

        String passwordHash = jdbcTemplate.queryForObject(
                "select password_hash from sys_user where id = ?", String.class, teacher.id());
        assertThat(passwordEncoder.matches("NewPassword123", passwordHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from auth_device_session where user_id = ? and status = 'ACTIVE'
                """, Integer.class, teacher.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from sys_iam_change_audit
                where target_id = ? and event_type = 'USER_PASSWORD_RESET'
                """, Integer.class, teacher.id())).isEqualTo(1);
    }

    @Test
    void transfersPendingReviewsToOrganizationAdministratorBeforeDisablingTeacher() {
        Fixture fixture = createFixture("teacher_manage_pending");
        TeacherAccount teacher = createTeacher(fixture, "teacher_pending_account", "待审核教师", null);
        Long studentId = idGenerator.nextId();
        Long taskId = idGenerator.nextId();
        Long assignmentId = idGenerator.nextId();
        jdbcTemplate.update("insert into edu_student (id, student_name) values (?, ?)", studentId, "待审核学生");
        jdbcTemplate.update("""
                insert into learn_task (
                    id, source_type, source_organization_id, creator_user_id, title,
                    difficulty_level, base_points, duration_minutes, scheduled_date,
                    reviewer_user_id, review_timeout_hours, status
                ) values (?, 'ORGANIZATION', ?, ?, '待审核保护任务', 1, 10, 30, ?, ?, 72, 'PUBLISHED')
                """, taskId, fixture.school().id(), fixture.currentUser().userId(),
                LocalDate.now(), teacher.id());
        jdbcTemplate.update("""
                insert into learn_task_assignment (
                    id, task_id, student_id, source_type, source_organization_id,
                    current_status, current_reviewer_id, scheduled_date, due_at
                ) values (?, ?, ?, 'ORGANIZATION', ?, 'PENDING_REVIEW', ?, ?, ?)
                """, assignmentId, taskId, studentId, fixture.school().id(), teacher.id(),
                LocalDate.now(), LocalDateTime.now().plusDays(1));

        service.changeStatus(fixture.currentUser(), teacher.id(), UserStatus.DISABLED);

        assertThat(jdbcTemplate.queryForObject(
                "select status from sys_user where id = ?", String.class, teacher.id()))
                .isEqualTo("DISABLED");
        assertThat(jdbcTemplate.queryForObject("""
                select current_reviewer_id from learn_task_assignment where id = ?
                """, Long.class, assignmentId)).isEqualTo(fixture.currentUser().userId());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_reviewer_transfer
                where assignment_id = ?
                  and from_reviewer_user_id = ?
                  and to_reviewer_user_id = ?
                """, Integer.class, assignmentId, teacher.id(), fixture.currentUser().userId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_assignment_event
                where assignment_id = ? and event_type = 'REVIEWER_TRANSFERRED'
                """, Integer.class, assignmentId)).isEqualTo(1);
    }

    private TeacherAccount createTeacher(
            Fixture fixture,
            String username,
            String displayName,
            String mobile
    ) {
        return service.create(fixture.currentUser(), new CreateTeacherCommand(
                username, displayName, mobile, "Password123", fixture.school().id(), List.of()));
    }

    @Test
    void teacherListAndDetailsHideActiveClassesOutsideOperatorOrganizationScope() {
        var fixture = createFixture("teacher_meta_scope");
        var own = createClass(fixture.school(), "TEACHER_META_OWN", "范围内班级");
        var outside = createClass(fixture.outsideSchool(), "TEACHER_META_OUTSIDE", "范围外班级");
        var teacher = service.create(fixture.currentUser(), new CreateTeacherCommand(
                "teacher_meta_account", "跨校教师", null, "Password123", fixture.school().id(), List.of(own.id())));
        jdbcTemplate.update("INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(?,?,?,'ACTIVE')",
                idGenerator.nextId(), teacher.id(), outside.id());
        assertThat(service.get(fixture.currentUser(), teacher.id()).classOrganizationIds()).containsExactly(own.id());
        var page = service.list(fixture.currentUser(), new TeacherQuery("teacher_meta_account", null, null, null, 1, 20));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).classOrganizationIds()).containsExactly(own.id());
    }

    private Fixture createFixture(String suffix) {
        Organization school = createSchool(suffix + "_school", "授权学校" + suffix);
        Organization outsideSchool = createSchool(suffix + "_outside", "范围外学校" + suffix);
        User administrator = userAccessApplicationService.createUser(new CreateUserCommand(
                suffix + "_admin", "教师管理机构管理员", null, UserType.ORGANIZATION));
        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(
                administrator.id(), school.id()));
        Role role = roleMapper.findByCode("ORG_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(
                administrator.id(), role.id(), school.id()));
        organizationAdminMapper.insert(idGenerator.nextId(), administrator.id(), school.id());
        AuthenticatedUser currentUser = new AuthenticatedUser(
                administrator.id(), idGenerator.nextId(), administrator.username(), administrator.displayName(),
                AuthClientType.WEB, List.of("ORG_ADMIN"));
        return new Fixture(currentUser, school, outsideSchool);
    }

    private Organization createSchool(String code, String name) {
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(code.toUpperCase(), name, "SCHOOL", null, 10));
    }

    private Organization createClass(Organization school, String code, String name) {
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(code, name, "CLASS", school.id(), 10));
    }

    private record Fixture(
            AuthenticatedUser currentUser,
            Organization school,
            Organization outsideSchool
    ) {
    }
}
