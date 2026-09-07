package com.lingdong.learning.organization.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ClassManagementApplicationServiceTest {
    @Autowired
    private ClassManagementApplicationService classManagementApplicationService;

    @Autowired
    private OrganizationApplicationService organizationApplicationService;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private OrganizationAdminMapper organizationAdminMapper;

    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private IdGenerator idGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsOnlyAuthorizedSchoolsAndCreatesClassWithServerGeneratedCode() {
        Fixture fixture = createFixture("class_manage_a");

        List<Organization> schools = classManagementApplicationService.listManageableSchools(fixture.currentUser());
        Organization created = classManagementApplicationService.createClass(
                fixture.currentUser(), new CreateClassCommand(fixture.school().id(), "一年级一班", 10));

        assertThat(schools).extracting(Organization::id).containsExactly(fixture.school().id());
        assertThat(created.typeCode()).isEqualTo("CLASS");
        assertThat(created.parentId()).isEqualTo(fixture.school().id());
        assertThat(created.code()).matches("CLS_[0-9]{19}");
        assertThat(Long.toString(created.id())).hasSize(19);
        assertThat(created.versionNo()).isEqualTo(1);
        Integer auditCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_organization_change_audit
                WHERE organization_id = ? AND event_type = 'CREATE'
                  AND operator_user_id = ?
                """, Integer.class, created.id(), fixture.currentUser().userId());
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void rejectsOutsideSchoolNonSchoolParentAndDuplicateClassName() {
        Fixture fixture = createFixture("class_manage_b");
        Organization region = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("REGION_CLASS_MANAGE_B", "班级管理区域乙", "REGION", null, 1));

        classManagementApplicationService.createClass(
                fixture.currentUser(), new CreateClassCommand(fixture.school().id(), "二年级一班", 10));

        assertThatThrownBy(() -> classManagementApplicationService.createClass(
                fixture.currentUser(), new CreateClassCommand(fixture.outsideSchool().id(), "范围外班级", 10)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> classManagementApplicationService.createClass(
                fixture.currentUser(), new CreateClassCommand(region.id(), "非学校班级", 10)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> classManagementApplicationService.createClass(
                fixture.currentUser(), new CreateClassCommand(fixture.school().id(), "二年级一班", 20)))
                .isInstanceOf(DuplicateOrganizationNameException.class);
    }

    @Test
    void listsDisabledClassesAndEditsWithOptimisticVersion() {
        Fixture fixture = createFixture("class_manage_c");
        Organization ownClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "CLASS_MANAGE_C_OWN", "待编辑班级", "CLASS", fixture.school().id(), 10));
        organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "CLASS_MANAGE_C_OUTSIDE", "范围外班级", "CLASS", fixture.outsideSchool().id(), 10));
        jdbcTemplate.update("""
                UPDATE sys_organization
                SET status = 'DISABLED', effective_status = 'DISABLED'
                WHERE id = ?
                """, ownClass.id());

        List<Organization> classes = classManagementApplicationService.listClasses(fixture.currentUser());
        Organization updated = classManagementApplicationService.updateClass(
                fixture.currentUser(), new UpdateClassCommand(
                        ownClass.id(), "编辑后班级", 30, ownClass.versionNo()));

        assertThat(classes).extracting(Organization::id).containsExactly(ownClass.id());
        assertThat(updated.name()).isEqualTo("编辑后班级");
        assertThat(updated.sortOrder()).isEqualTo(30);
        assertThat(updated.versionNo()).isEqualTo(ownClass.versionNo() + 1);
        assertThatThrownBy(() -> classManagementApplicationService.updateClass(
                fixture.currentUser(), new UpdateClassCommand(
                        ownClass.id(), "过期页面名称", 40, ownClass.versionNo())))
                .isInstanceOf(OrganizationVersionConflictException.class);
    }

    @Test
    void disablesClassAndInvalidatesOnlyUnfinishedInstitutionAssignmentsInThatClass() {
        Fixture fixture = createFixture("class_manage_d");
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "CLASS_MANAGE_D_OWN", "停用联动班级", "CLASS", fixture.school().id(), 10));
        Organization outsideClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "CLASS_MANAGE_D_OUT", "停用范围外班级", "CLASS", fixture.outsideSchool().id(), 10));
        long insideStudentId = insertStudentInClass(classOrganization.id(), "停用班内学生");
        long outsideStudentId = insertStudentInClass(outsideClass.id(), "停用班外学生");
        long organizationPending = insertTaskAssignment(
                insideStudentId, "ORGANIZATION", classOrganization.id(), "PENDING_CLAIM");
        long teacherInProgress = insertTaskAssignment(
                insideStudentId, "TEACHER", classOrganization.id(), "IN_PROGRESS");
        long familyPending = insertTaskAssignment(
                insideStudentId, "FAMILY", null, "PENDING_CLAIM");
        long organizationCompleted = insertTaskAssignment(
                insideStudentId, "ORGANIZATION", classOrganization.id(), "COMPLETED");
        long organizationExempt = insertTaskAssignment(
                insideStudentId, "ORGANIZATION", classOrganization.id(), "EXEMPT");
        long outsidePending = insertTaskAssignment(
                outsideStudentId, "ORGANIZATION", outsideClass.id(), "PENDING_CLAIM");
        long pauseId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO learn_task_pause (
                    id, assignment_id, pause_type, started_by_user_id, started_at, expires_at
                ) VALUES (?, ?, 'EMOTION', ?, ?, ?)
                """, pauseId, teacherInProgress, fixture.currentUser().userId(),
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(30));

        Organization disabled = classManagementApplicationService.disableClass(
                fixture.currentUser(), classOrganization.id(), classOrganization.versionNo());

        assertThat(disabled.status().name()).isEqualTo("DISABLED");
        assertThat(disabled.effectiveStatus().name()).isEqualTo("DISABLED");
        assertThat(assignmentStatus(organizationPending)).isEqualTo("INVALIDATED");
        assertThat(assignmentStatus(teacherInProgress)).isEqualTo("INVALIDATED");
        assertThat(assignmentStatus(familyPending)).isEqualTo("PENDING_CLAIM");
        assertThat(assignmentStatus(organizationCompleted)).isEqualTo("COMPLETED");
        assertThat(assignmentStatus(organizationExempt)).isEqualTo("EXEMPT");
        assertThat(assignmentStatus(outsidePending)).isEqualTo("PENDING_CLAIM");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM learn_task_assignment_event
                WHERE assignment_id IN (?, ?) AND event_type = 'CLASS_INVALIDATED'
                """, Integer.class, organizationPending, teacherInProgress)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT close_type FROM learn_task_pause WHERE id = ?
                """, String.class, pauseId)).isEqualTo("TERMINATED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_organization_change_audit
                WHERE organization_id = ? AND event_type = 'DISABLE'
                """, Integer.class, classOrganization.id())).isEqualTo(1);

        Organization enabled = classManagementApplicationService.enableClass(
                fixture.currentUser(), classOrganization.id(), disabled.versionNo());
        assertThat(enabled.status().name()).isEqualTo("ENABLED");
        assertThat(assignmentStatus(organizationPending)).isEqualTo("INVALIDATED");
    }

    private Fixture createFixture(String suffix) {
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        ("SCHOOL_" + suffix).toUpperCase(), "授权学校" + suffix, "SCHOOL", null, 10));
        Organization outsideSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        ("OUTSIDE_" + suffix).toUpperCase(), "范围外学校" + suffix, "SCHOOL", null, 20));
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(suffix, "班级机构管理员" + suffix, null, UserType.ORGANIZATION));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(user.id(), school.id()));
        Role role = roleMapper.findByCode("ORG_ADMIN");
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), role.id(), school.id()));
        organizationAdminMapper.insert(idGenerator.nextId(), user.id(), school.id());
        AuthenticatedUser currentUser = new AuthenticatedUser(
                user.id(), idGenerator.nextId(), user.username(), user.displayName(),
                AuthClientType.WEB, List.of("ORG_ADMIN"));
        return new Fixture(currentUser, school, outsideSchool);
    }

    private long insertStudentInClass(Long classId, String name) {
        long studentId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO edu_student (id, student_name, status)
                VALUES (?, ?, 'ENABLED')
                """, studentId, name);
        jdbcTemplate.update("""
                INSERT INTO edu_student_organization (
                    id, student_id, organization_id, relation_type, status
                ) VALUES (?, ?, ?, 'CLASS', 'ACTIVE')
                """, idGenerator.nextId(), studentId, classId);
        return studentId;
    }

    private long insertTaskAssignment(
            long studentId,
            String sourceType,
            Long sourceOrganizationId,
            String status
    ) {
        long taskId = idGenerator.nextId();
        long assignmentId = idGenerator.nextId();
        LocalDate scheduledDate = LocalDate.now();
        jdbcTemplate.update("""
                INSERT INTO learn_task (
                    id, source_type, source_organization_id, creator_user_id, title,
                    difficulty_level, base_points, duration_minutes, scheduled_date,
                    reviewer_user_id, status
                ) VALUES (?, ?, ?, ?, ?, 1, 10, 30, ?, ?, 'PUBLISHED')
                """, taskId, sourceType, sourceOrganizationId,
                currentOperatorId(), "班级停用联动任务" + taskId,
                scheduledDate, currentOperatorId());
        jdbcTemplate.update("""
                INSERT INTO learn_task_assignment (
                    id, task_id, student_id, source_type, source_organization_id,
                    current_status, current_reviewer_id, scheduled_date, due_at,
                    completed_at, last_transition_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, assignmentId, taskId, studentId, sourceType, sourceOrganizationId,
                status, currentOperatorId(), scheduledDate, scheduledDate.atTime(23, 59),
                "COMPLETED".equals(status) ? LocalDateTime.now() : null, LocalDateTime.now());
        return assignmentId;
    }

    private Long currentOperatorId() {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM sys_user
                WHERE username = 'class_manage_d'
                """, Long.class);
    }

    private String assignmentStatus(long assignmentId) {
        return jdbcTemplate.queryForObject("""
                SELECT current_status FROM learn_task_assignment WHERE id = ?
                """, String.class, assignmentId);
    }

    private record Fixture(
            AuthenticatedUser currentUser,
            Organization school,
            Organization outsideSchool
    ) {
    }
}
