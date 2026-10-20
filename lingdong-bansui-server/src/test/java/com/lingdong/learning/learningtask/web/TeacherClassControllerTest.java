package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.common.id.IdGenerator;
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
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TeacherClassControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private IdGenerator idGenerator;
    @Autowired private org.apache.ibatis.session.SqlSession sqlSession;

    @Test
    void bindsListsDeactivatesAndReactivatesTeacherClassRelations() throws Exception {
        User systemAdministrator = createUserWithRole(
                "teacher_class_sys_admin", "教师班级系统管理员", "SYS_ADMIN", null);
        User organizationAdministrator = createUser(
                "teacher_class_org_admin", "教师班级机构管理员");
        User teacher = createUser("teacher_class_teacher", "教师班级测试教师");
        User ordinaryUser = createUser("teacher_class_ordinary", "非教师用户");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_SCHOOL", "教师班级学校", "SCHOOL", null, 10));
        Organization schoolGrade = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_ONE_GRADE", "测试年级", "GRADE", school.id(), 10));
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_ONE", "教师班级一班", "CLASS", schoolGrade.id(), 10));
        Organization outsideSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_OUTSIDE_SCHOOL", "教师范围外学校", "SCHOOL", null, 20));
        Organization outsideSchoolGrade = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_OUTSIDE_GRADE", "测试年级", "GRADE", outsideSchool.id(), 10));
        Organization outsideClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_OUTSIDE", "教师范围外班级", "CLASS", outsideSchoolGrade.id(), 10));

        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id()));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(teacher.id(), school.id()));
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        assignRole(teacher, "TEACHER", school.id());
        organizationAdminMapper.insert(
                1_874_244_142_494_646_402L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, systemAdministrator);
        setPassword(systemAdministrator, organizationAdministrator);
        setPassword(systemAdministrator, teacher);
        // V135 后教师职业身份独立持久化：需机构账号类型与启用教师档案，否则教师分班被判非启用教师
        makeTeacher("teacher_class_teacher");
        String administratorToken = loginAccessToken("teacher_class_org_admin");
        String teacherToken = loginAccessToken("teacher_class_teacher");

        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherUserId").value(teacher.id().toString()))
                .andExpect(jsonPath("$.classOrganizationId").value(classOrganization.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from edu_teacher_class_change_log
                where teacher_user_id = ? and class_organization_id = ? and event_type = 'BIND'
                """, Integer.class, teacher.id(), classOrganization.id())).isEqualTo(1);

        mockMvc.perform(get("/api/v1/teachers/{teacherUserId}/classes", teacher.id())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].classOrganizationId").value(classOrganization.id().toString()));

        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), outsideClass.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        ordinaryUser.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Long studentId = idGenerator.nextId();
        Long taskId = idGenerator.nextId();
        Long assignmentId = idGenerator.nextId();
        jdbcTemplate.update("insert into edu_student (id, student_name) values (?, ?)", studentId, "班级解绑保护学生");
        jdbcTemplate.update("""
                insert into edu_student_organization
                    (id, student_id, organization_id, relation_type, status)
                values (?, ?, ?, 'CLASS', 'ACTIVE')
                """, idGenerator.nextId(), studentId, classOrganization.id());
        jdbcTemplate.update("""
                insert into learn_task (
                    id, source_type, source_organization_id, creator_user_id, title,
                    difficulty_level, base_points, duration_minutes, scheduled_date,
                    reviewer_user_id, review_timeout_hours, status
                ) values (?, 'ORGANIZATION', ?, ?, '班级解绑保护任务', 1, 10, 30, ?, ?, 72, 'PUBLISHED')
                """, taskId, school.id(), organizationAdministrator.id(), LocalDate.now(), teacher.id());
        jdbcTemplate.update("""
                insert into learn_task_assignment (
                    id, task_id, student_id, source_type, source_organization_id,
                    current_status, current_reviewer_id, scheduled_date, due_at
                ) values (?, ?, ?, 'ORGANIZATION', ?, 'PENDING_REVIEW', ?, ?, ?)
                """, assignmentId, taskId, studentId, school.id(), teacher.id(), LocalDate.now(),
                LocalDateTime.now().plusDays(1));

        mockMvc.perform(delete("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("""
                select current_reviewer_id from learn_task_assignment where id = ?
                """, Long.class, assignmentId)).isEqualTo(organizationAdministrator.id());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_reviewer_transfer
                where assignment_id = ?
                  and from_reviewer_user_id = ?
                  and to_reviewer_user_id = ?
                """, Integer.class, assignmentId, teacher.id(), organizationAdministrator.id()))
                .isEqualTo(1);

        mockMvc.perform(get("/api/v1/teachers/{teacherUserId}/classes", teacher.id())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk());

        Integer relationCount = jdbcTemplate.queryForObject("""
                select count(*) from edu_teacher_class
                where teacher_user_id = ? and class_organization_id = ?
                """, Integer.class, teacher.id(), classOrganization.id());
        String statusValue = jdbcTemplate.queryForObject("""
                select status from edu_teacher_class
                where teacher_user_id = ? and class_organization_id = ?
                """, String.class, teacher.id(), classOrganization.id());
        assertThat(relationCount).isEqualTo(1);
        assertThat(statusValue).isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForList("""
                select id from edu_teacher_class_change_log
                where teacher_user_id = ? and class_organization_id = ?
                order by created_at, id
                """, Long.class, teacher.id(), classOrganization.id()))
                .hasSize(3)
                .allSatisfy(id -> assertThat(Long.toString(id)).hasSize(19));
    }

    @Test
    void rejectsTeacherOutsideCampusScopeWithoutBlockingSchoolAdministrator() throws Exception {
        User system = createUserWithRole("source_scope_system", "系统管理员", "SYS_ADMIN", null);
        User campusAdmin = createUser("source_scope_campus_admin", "校区管理员");
        User teacher = createUser("source_scope_teacher", "同名教师");
        Organization school = organizationApplicationService.createOrganization(new CreateOrganizationCommand("SRC_SCHOOL", "源范围学校", "SCHOOL", null, 10));
        Organization a = organizationApplicationService.createOrganization(new CreateOrganizationCommand("SRC_A", "A校区", "CAMPUS", school.id(), 10));
        Organization b = organizationApplicationService.createOrganization(new CreateOrganizationCommand("SRC_B", "B校区", "CAMPUS", school.id(), 20));
        Organization grade = organizationApplicationService.createOrganization(new CreateOrganizationCommand("SRC_GRADE", "一年级", "GRADE", a.id(), 10));
        Organization clazz = organizationApplicationService.createOrganization(new CreateOrganizationCommand("SRC_CLASS", "一班", "CLASS", grade.id(), 10));
        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(campusAdmin.id(), school.id()));
        assignRole(campusAdmin, "ORG_ADMIN", school.id());
        organizationAdminMapper.insert(idGenerator.nextId(), campusAdmin.id(), a.id());
        userAccessApplicationService.associateWithOrganization(new AssociateUserWithOrganizationCommand(teacher.id(), b.id()));
        assignRole(teacher, "TEACHER", b.id());
        setPassword(system, system);
        setPassword(system, campusAdmin);
        // V135 后教师职业身份独立持久化：需机构账号类型与启用教师档案，否则分班被判非启用教师
        makeTeacher("source_scope_teacher");
        String campusToken = loginAccessToken(campusAdmin.username());
        String systemToken = loginAccessToken(system.username());
        mockMvc.perform(get("/api/v1/learning-task-options/teachers").param("classId", clazz.id().toString()).header("Authorization", "Bearer " + campusToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/v1/teachers/{teacher}/classes/{clazz}", teacher.id(), clazz.id()).header("Authorization", "Bearer " + campusToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/teachers/{teacher}/classes/{clazz}", teacher.id(), clazz.id()).header("Authorization", "Bearer " + systemToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/teachers/{teacher}/classes/{clazz}", teacher.id(), clazz.id()).header("Authorization", "Bearer " + campusToken))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select status from edu_teacher_class where teacher_user_id=? and class_organization_id=?", String.class, teacher.id(), clazz.id())).isEqualTo("ACTIVE");
        mockMvc.perform(get("/api/v1/learning-task-options/teachers").param("classId", clazz.id().toString()).header("Authorization", "Bearer " + systemToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].organizationNames[0]").value("源范围学校·B校区"));
    }

    private User createUserWithRole(String username, String displayName, String roleCode, Long organizationId) {
        User user = createUser(username, displayName);
        assignRole(user, roleCode, organizationId);
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }

    private void assignRole(User user, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), organizationId));
    }

    private void setPassword(User systemAdministrator, User targetUser) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                systemAdministrator.id(), targetUser.id(), "Password123"));
        // bf34c6a 管理员设密后要求首登改密，业务测试直接清除首登改密标记
        jdbcTemplate.update("UPDATE auth_initial_password_state SET change_required=false WHERE user_id=?", targetUser.id());
    }

    /** V135 后教师职业身份独立持久化：机构账号类型 + edu_teacher_profile 启用记录。 */
    private void makeTeacher(String username) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, username);
        jdbcTemplate.update("UPDATE sys_user SET user_type='ORGANIZATION' WHERE id=?", id);
        jdbcTemplate.update("INSERT INTO edu_teacher_profile(user_id,status) SELECT ?,'ENABLED' "
                + "WHERE NOT EXISTS(SELECT 1 FROM edu_teacher_profile WHERE user_id=?)", id, id);
        // 测试事务跨越多个 HTTP 请求，清除 JDBC 夹具变更前的 MyBatis 一级缓存
        sqlSession.clearCache();
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"教师班级测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessToken").asText();
    }
}
