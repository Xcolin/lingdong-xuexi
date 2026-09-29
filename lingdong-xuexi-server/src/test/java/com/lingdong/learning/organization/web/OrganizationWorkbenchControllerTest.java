package com.lingdong.learning.organization.web;

import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.OrganizationPasswordLoginCommand;
import com.lingdong.learning.auth.application.PasswordLoginCommand;
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
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrganizationWorkbenchControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IdGenerator idGenerator;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void returnsOnlyCurrentAdministratorsDirectEnabledOrganizationsInStableOrder() throws Exception {
        User current = createAdministrator("workbench_current", "当前机构管理员");
        Organization second = createSchool("WORKBENCH_B", "第二学校", 20);
        Organization first = createSchool("WORKBENCH_A", "第一学校", 10);
        Organization disabled = createSchool("WORKBENCH_DISABLED", "停用学校", 5);
        assign(current, second);
        assign(current, first);
        assign(current, disabled);
        jdbcTemplate.update("update sys_organization set status = 'DISABLED' where id = ?", disabled.id());

        User other = createAdministrator("workbench_other", "其他机构管理员");
        Organization otherSchool = createSchool("WORKBENCH_OTHER", "其他学校", 1);
        assign(other, otherSchool);

        AuthenticatedSession session = loginMiniapp(current, "workbench-miniapp");

        mockMvc.perform(get("/api/v1/organization-workbench/context")
                        .header("Authorization", bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.userId").value(current.id().toString()))
                .andExpect(jsonPath("$.username").value(current.username()))
                .andExpect(jsonPath("$.displayName").value(current.displayName()))
                .andExpect(jsonPath("$.permissionCodes").isArray())
                .andExpect(jsonPath("$.permissionCodes", org.hamcrest.Matchers.hasItem("TEACHER_READ")))
                .andExpect(jsonPath("$.organizations.length()").value(2))
                .andExpect(jsonPath("$.organizations[0].id").isString())
                .andExpect(jsonPath("$.organizations[0].name").value("第一学校"))
                .andExpect(jsonPath("$.organizations[0].typeCode").value("SCHOOL"))
                .andExpect(jsonPath("$.organizations[1].name").value("第二学校"))
                .andExpect(jsonPath("$..organizationPath").doesNotExist())
                .andExpect(jsonPath("$..parentId").doesNotExist())
                .andExpect(jsonPath("$..administrator").doesNotExist());
    }

    @Test
    void rejectsAnonymousWebAndFeatureDisabledOrganizationSessions() throws Exception {
        mockMvc.perform(get("/api/v1/organization-workbench/context"))
                .andExpect(status().isUnauthorized());

        User administrator = createAdministrator("workbench_boundary", "工作台边界管理员");
        assign(administrator, createSchool("WORKBENCH_BOUNDARY", "工作台边界学校", 10));
        AuthenticatedSession webSession = authenticationApplicationService.loginByPassword(
                new PasswordLoginCommand(
                        administrator.username(), "ValidPass123!", "workbench-web", "机构浏览器"));
        mockMvc.perform(get("/api/v1/organization-workbench/context")
                        .header("Authorization", bearer(webSession.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        AuthenticatedSession miniappSession = loginMiniapp(administrator, "workbench-disabled");
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'ORGANIZATION_MINIAPP_AUTH'
                """);
        mockMvc.perform(get("/api/v1/organization-workbench/context")
                        .header("Authorization", bearer(miniappSession.accessToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void rejectsWebOnlyPermissionsWhenUsingAnOrganizationMiniappSession() throws Exception {
        User administrator = createAdministrator("workbench_client_boundary", "客户端边界管理员");
        Organization school = createSchool("WORKBENCH_CLIENT_BOUNDARY", "客户端边界学校", 10);
        assign(administrator, school);
        AuthenticatedSession miniappSession = loginMiniapp(administrator, "workbench-client-boundary");

        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", bearer(miniappSession.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentName":"越权创建学生","gradeCode":"G4","organizationId":"%s"}
                                """.formatted(school.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from edu_student where student_name = '越权创建学生'",
                Integer.class)).isZero();
    }

    @Test
    void managesOnlyCurrentOrganizationStudentsWithABothClientPermission() throws Exception {
        User administrator = createAdministrator("workbench_student_lifecycle", "学员关系管理员");
        Organization school = createSchool("WORKBENCH_STUDENT_SCHOOL", "学员关系学校", 10);
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "WORKBENCH_STUDENT_CLASS", "学员关系一班", "CLASS", school.id(), 10));
        assign(administrator, school);
        Long studentId = idGenerator.nextId();
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, status)
                values (?, '小程序学员', 'ENABLED')
                """, studentId);
        jdbcTemplate.update("""
                insert into edu_student_organization
                    (id, student_id, organization_id, relation_type, status)
                values (?, ?, ?, 'ENROLLMENT', 'ACTIVE')
                """, idGenerator.nextId(), studentId, school.id());
        AuthenticatedSession session = loginMiniapp(administrator, "workbench-student-lifecycle");

        mockMvc.perform(get("/api/v1/students/organization-relationships")
                        .header("Authorization", bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(studentId.toString()))
                .andExpect(jsonPath("$[0].studentName").value("小程序学员"))
                .andExpect(jsonPath("$[0].enrollmentOrganizationId").value(school.id().toString()));

        mockMvc.perform(get("/api/v1/students/organization-relationship-classes")
                        .header("Authorization", bearer(session.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(classOrganization.id().toString()))
                .andExpect(jsonPath("$[0].name").value("学员关系一班"));

        mockMvc.perform(post("/api/v1/students/{studentId}/class-transfers", studentId)
                        .header("Authorization", bearer(session.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"classOrganizationId":"%s","reason":"移动端分班"}
                                """.formatted(classOrganization.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentClassOrganizationId")
                        .value(classOrganization.id().toString()));
    }

    private User createAdministrator(String username, String displayName) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.ORGANIZATION));
        assertThat(userMapper.updatePasswordHash(
                user.id(), passwordEncoder.encode("ValidPass123!"))).isEqualTo(1);
        return userMapper.findById(user.id());
    }

    private Organization createSchool(String code, String name, int sortOrder) {
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(code, name, "SCHOOL", null, sortOrder));
    }

    private void assign(User user, Organization organization) {
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(user.id(), organization.id()));
        Role role = roleMapper.findByCode("ORG_ADMIN");
        userAccessApplicationService.assignRole(
                new AssignRoleToUserCommand(user.id(), role.id(), organization.id()));
        assertThat(organizationAdminMapper.insert(
                idGenerator.nextId(), user.id(), organization.id())).isEqualTo(1);
    }

    private AuthenticatedSession loginMiniapp(User user, String deviceId) {
        return authenticationApplicationService.loginOrganizationByPassword(
                new OrganizationPasswordLoginCommand(
                        user.username(), "ValidPass123!", deviceId, "机构管理员手机"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
