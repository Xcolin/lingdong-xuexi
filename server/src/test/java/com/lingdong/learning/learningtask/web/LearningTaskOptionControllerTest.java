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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LearningTaskOptionControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;

    @Test
    void returnsOnlyRoleScopedAndMaskedTaskOptions() throws Exception {
        User systemAdministrator = createUserWithRole(
                "task_option_sys_admin", "任务候选系统管理员", "SYS_ADMIN", null);
        User parent = createUserWithRole("task_option_parent", "任务候选家长", "PARENT", null);
        User organizationAdministrator = createUser("task_option_org_admin", "任务候选机构管理员");
        User teacher = createUser("task_option_teacher", "任务候选教师");
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_SCHOOL", "任务候选学校", "SCHOOL", null, 10));
        Organization classOrganization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_CLASS", "任务候选一班", "CLASS", school.id(), 10));
        Organization outsideSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_OUTSIDE_SCHOOL", "候选范围外学校", "SCHOOL", null, 20));
        organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("TASK_OPTION_OUTSIDE_CLASS", "候选范围外班级", "CLASS", outsideSchool.id(), 10));

        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id()));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(teacher.id(), school.id()));
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        assignRole(teacher, "TEACHER", school.id());
        organizationAdminMapper.insert(
                1_874_244_142_494_646_403L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, systemAdministrator);
        setPassword(systemAdministrator, parent);
        setPassword(systemAdministrator, organizationAdministrator);
        setPassword(systemAdministrator, teacher);

        String parentToken = loginAccessToken("task_option_parent");
        String administratorToken = loginAccessToken("task_option_org_admin");
        String teacherToken = loginAccessToken("task_option_teacher");
        Long familyStudentId = createStudent(parentToken, "家庭候选学生", null);
        Long organizationStudentId = createStudent(administratorToken, "机构候选学生", school.id());

        mockMvc.perform(put("/api/v1/students/{studentId}/class", organizationStudentId)
                        .header("Authorization", "Bearer " + administratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(classOrganization.id())))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/teachers/{teacherUserId}/classes/{classId}",
                        teacher.id(), classOrganization.id())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "FAMILY")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(familyStudentId.toString()))
                .andExpect(jsonPath("$[0].studentAccountMasked").value("26****01"))
                .andExpect(jsonPath("$[0].studentAccount").doesNotExist())
                .andExpect(jsonPath("$[0].mobile").doesNotExist())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/learning-task-options/organizations")
                        .param("sourceType", "ORGANIZATION")
                        .param("organizationType", "CLASS")
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(classOrganization.id().toString()))
                .andExpect(jsonPath("$[0].name").value("任务候选一班"));

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "ORGANIZATION")
                        .param("organizationId", classOrganization.id().toString())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(organizationStudentId.toString()))
                .andExpect(jsonPath("$[0].currentClassId").value(classOrganization.id().toString()));

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "TEACHER")
                        .param("organizationId", classOrganization.id().toString())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(organizationStudentId.toString()));

        mockMvc.perform(get("/api/v1/learning-task-options/teachers")
                        .param("classId", classOrganization.id().toString())
                        .header("Authorization", "Bearer " + administratorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(teacher.id().toString()))
                .andExpect(jsonPath("$[0].displayName").value("任务候选教师"))
                .andExpect(jsonPath("$[0].classOrganizationIds[0]").value(classOrganization.id().toString()))
                .andExpect(jsonPath("$[0].mobile").doesNotExist());

        mockMvc.perform(get("/api/v1/learning-task-options/students")
                        .param("sourceType", "ORGANIZATION")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Long createStudent(String accessToken, String name, Long organizationId) throws Exception {
        String organizationField = organizationId == null
                ? ""
                : ",\"organizationId\":\"%s\"".formatted(organizationId);
        MvcResult result = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"%s\"%s}".formatted(name, organizationField)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
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
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"任务候选测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessToken").asText();
    }
}
