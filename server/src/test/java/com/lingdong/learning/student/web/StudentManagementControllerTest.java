package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FeatureToggleMapper featureToggleMapper;

    @Test
    void createsFamilyAndOrganizationStudentsWithinTheirOwnScopes() throws Exception {
        User systemAdministrator = createUserWithRole("student_scope_sys_admin", "学生范围系统管理员", UserType.PLATFORM, "SYS_ADMIN");
        User parent = createUserWithRole("student_scope_parent", "学生范围家长", UserType.PLATFORM, "PARENT");
        User organizationAdministrator = createUser("student_scope_org_admin", "学生范围机构管理员", UserType.PLATFORM);
        User ordinaryUser = createUser("student_scope_ordinary", "学生范围普通用户", UserType.PLATFORM);
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("STUDENT_SCOPE_SCHOOL", "学生范围测试学校", "SCHOOL", null, 10)
        );
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id())
        );
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        organizationAdminMapper.insert(1_874_244_142_494_646_400L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, parent);
        setPassword(systemAdministrator, organizationAdministrator);
        setPassword(systemAdministrator, ordinaryUser);
        setPassword(systemAdministrator, systemAdministrator);

        MvcResult familyStudentResult = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_parent"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"家庭学生\",\"gradeCode\":\"G3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.studentName").value("家庭学生"))
                .andExpect(jsonPath("$.studentAccount").isString())
                .andExpect(jsonPath("$.initialLoginCode").isString())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        JsonNode familyStudentResponse = objectMapper.readTree(familyStudentResult.getResponse().getContentAsString());
        Long familyStudentId = familyStudentResponse.path("id").asLong();
        String familyStudentAccount = familyStudentResponse.path("studentAccount").asText();
        assertThat(familyStudentAccount).matches("\\d{8}");
        assertThat(familyStudentResponse.path("initialLoginCode").asText()).matches("\\d{4}");

        MvcResult organizationStudentResult = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_org_admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"机构学生\",\"gradeCode\":\"G4\",\"organizationId\":\"%s\"}"
                                .formatted(school.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.studentName").value("机构学生"))
                .andExpect(jsonPath("$.studentAccount").isString())
                .andExpect(jsonPath("$.initialLoginCode").isString())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();
        JsonNode organizationStudentResponse = objectMapper.readTree(
                organizationStudentResult.getResponse().getContentAsString());
        Long organizationStudentId = organizationStudentResponse.path("id").asLong();
        assertThat(organizationStudentResponse.path("studentAccount").asText()).matches("\\d{8}");
        assertThat(organizationStudentResponse.path("initialLoginCode").asText()).matches("\\d{4}");

        mockMvc.perform(get("/api/v1/students")
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_parent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(familyStudentId.toString()));

        mockMvc.perform(get("/api/v1/students/{id}", organizationStudentId)
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_parent")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/students/{id}", familyStudentId)
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_org_admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/students/{id}", familyStudentId)
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_parent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialLoginCode").doesNotExist());

        mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"student-device","deviceName":"学生设备"}
                                """.formatted(familyStudentAccount)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/students")
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_sys_admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/students")
                        .header("Authorization", "Bearer " + loginAccessToken("student_scope_ordinary")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void assignsAndSwitchesAStudentsCurrentClassWithinOrganizationScope() throws Exception {
        User systemAdministrator = createUserWithRole(
                "student_class_sys_admin", "班级配置系统管理员", UserType.PLATFORM, "SYS_ADMIN");
        User organizationAdministrator = createUser(
                "student_class_org_admin", "班级配置机构管理员", UserType.PLATFORM);
        Organization school = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("STUDENT_CLASS_SCHOOL", "班级配置学校", "SCHOOL", null, 10));
        Organization firstClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("STUDENT_CLASS_ONE", "一班", "CLASS", school.id(), 10));
        Organization secondClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("STUDENT_CLASS_TWO", "二班", "CLASS", school.id(), 20));
        Organization outsideSchool = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("STUDENT_CLASS_OUTSIDE_SCHOOL", "范围外学校", "SCHOOL", null, 20));
        Organization outsideClass = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand("STUDENT_CLASS_OUTSIDE", "范围外班级", "CLASS", outsideSchool.id(), 10));
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(organizationAdministrator.id(), school.id()));
        assignRole(organizationAdministrator, "ORG_ADMIN", school.id());
        organizationAdminMapper.insert(
                1_874_244_142_494_646_401L, organizationAdministrator.id(), school.id());
        setPassword(systemAdministrator, systemAdministrator);
        setPassword(systemAdministrator, organizationAdministrator);
        String accessToken = loginAccessToken("student_class_org_admin");

        MvcResult createResult = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentName\":\"待分班学生\",\"organizationId\":\"%s\"}"
                                .formatted(school.id())))
                .andExpect(status().isCreated())
                .andReturn();
        Long studentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(put("/api/v1/students/{studentId}/class", studentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(firstClass.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(studentId.toString()))
                .andExpect(jsonPath("$.classOrganizationId").value(firstClass.id().toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(put("/api/v1/students/{studentId}/class", studentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(secondClass.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classOrganizationId").value(secondClass.id().toString()));

        mockMvc.perform(put("/api/v1/students/{studentId}/class", studentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(secondClass.id())))
                .andExpect(status().isOk());

        Integer activeClassCount = jdbcTemplate.queryForObject("""
                select count(*) from edu_student_organization
                where student_id = ? and relation_type = 'CLASS' and status = 'ACTIVE'
                """, Integer.class, studentId);
        String activeClassId = jdbcTemplate.queryForObject("""
                select cast(organization_id as varchar) from edu_student_organization
                where student_id = ? and relation_type = 'CLASS' and status = 'ACTIVE'
                """, String.class, studentId);
        assertThat(activeClassCount).isEqualTo(1);
        assertThat(activeClassId).isEqualTo(secondClass.id().toString());

        mockMvc.perform(put("/api/v1/students/{studentId}/class", studentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(outsideClass.id())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/students/{studentId}/class", studentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(school.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        featureToggleMapper.updateGlobalStatus("LEARNING_TASK_MANAGEMENT", FeatureStatus.DISABLED);
        mockMvc.perform(put("/api/v1/students/{studentId}/class", studentId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classOrganizationId\":\"%s\"}".formatted(firstClass.id())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    private User createUserWithRole(String username, String displayName, UserType userType, String roleCode) {
        User user = createUser(username, displayName, userType);
        assignRole(user, roleCode, null);
        return user;
    }

    private User createUser(String username, String displayName, UserType userType) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, userType));
    }

    private void assignRole(User user, String roleCode, Long organizationId) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), organizationId));
    }

    private void setPassword(User systemAdministrator, User targetUser) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                systemAdministrator.id(), targetUser.id(), "Password123"
        ));
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"学生关系测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessToken").asText();
    }
}
