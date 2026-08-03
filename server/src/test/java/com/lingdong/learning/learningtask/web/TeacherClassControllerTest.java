package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
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
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_ONE", "教师班级一班", "CLASS", school.id(), 10));
        Organization outsideSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_OUTSIDE_SCHOOL", "教师范围外学校", "SCHOOL", null, 20));
        Organization outsideClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TEACHER_CLASS_OUTSIDE", "教师范围外班级", "CLASS", outsideSchool.id(), 10));

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

        mockMvc.perform(delete("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isNoContent());

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
