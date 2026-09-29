package com.lingdong.learning.interfaceconfig.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InterfaceServiceManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void registersServiceOnlyAfterSeparatedRoleApprovalAndListsStringIdentifiers() throws Exception {
        User owner = createUser("interface_api_owner");
        String administratorToken = tokenWithRole("interface_api_submitter", "SYS_ADMIN");
        String auditorToken = tokenWithRole("interface_api_auditor", "SYS_AUDITOR");

        String taskId = submitRegistration(administratorToken, owner.id(), "微信服务通知", "notification-adapter");
        mockMvc.perform(get("/api/v1/interface-services/review-queue")
                        .header("Authorization", bearer(auditorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.taskId == '%s')].taskStatus".formatted(taskId))
                        .value("PENDING_REVIEW"));

        mockMvc.perform(post("/api/v1/interface-services/review-tasks/{taskId}/approve", taskId)
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意登记\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"));

        mockMvc.perform(get("/api/v1/interface-services")
                        .param("callerName", "notification-adapter")
                        .header("Authorization", bearer(administratorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].ownerId").isString())
                .andExpect(jsonPath("$[0].serviceName").value("微信服务通知"))
                .andExpect(jsonPath("$[0].status").value("ENABLED"));
    }

    @Test
    void rejectsDisableWithoutChangingEffectiveService() throws Exception {
        User owner = createUser("interface_api_reject_owner");
        String administratorToken = tokenWithRole("interface_api_reject_admin", "SYS_ADMIN");
        String auditorToken = tokenWithRole("interface_api_reject_auditor", "SYS_AUDITOR");
        String registrationTaskId = submitRegistration(
                administratorToken, owner.id(), "学校同步", "school-sync-adapter");
        approve(auditorToken, registrationTaskId);
        String serviceId = firstServiceId(administratorToken, "school-sync-adapter");

        MvcResult submission = mockMvc.perform(post(
                            "/api/v1/interface-services/{serviceId}/disable-submissions", serviceId)
                        .header("Authorization", bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"停用学校同步\",\"description\":\"变更窗口内暂停调用\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = body(submission).path("taskId").asText();

        mockMvc.perform(post("/api/v1/interface-services/review-tasks/{taskId}/reject", taskId)
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"不在变更窗口\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/v1/interface-services")
                        .param("callerName", "school-sync-adapter")
                        .header("Authorization", bearer(administratorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ENABLED"));
    }

    @Test
    void rejectsMissingPermissionAndDisabledFeature() throws Exception {
        String ordinaryToken = tokenWithRole("interface_api_ordinary", "PARENT");
        mockMvc.perform(get("/api/v1/interface-services")
                        .header("Authorization", bearer(ordinaryToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String administratorToken = tokenWithRole("interface_api_disabled", "SYS_ADMIN");
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'INTERFACE_SERVICE_MANAGEMENT' and scope_key = 'GLOBAL'
                """);
        mockMvc.perform(get("/api/v1/interface-services")
                        .header("Authorization", bearer(administratorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void publishesInterfaceServiceManagementPathsInOpenApi() throws Exception {
        String token = tokenWithRole("interface_api_openapi", "SYS_ADMIN");
        mockMvc.perform(get("/api/v1/openapi").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/interface-services']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/interface-services/registration-submissions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/interface-services/review-queue']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/interface-services/review-tasks/{taskId}/approve']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/interface-services/review-tasks/{taskId}/reject']").exists());
    }

    private String submitRegistration(String token, Long ownerId, String serviceName, String callerName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/interface-services/registration-submissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceName":"%s","direction":"OUTBOUND","purpose":"WECHAT",
                                 "callerName":"%s","authorizationScope":"GLOBAL","ownerId":"%s",
                                 "title":"登记%s","description":"登记接口服务并纳入审批"}
                                """.formatted(serviceName, callerName, ownerId, serviceName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").isString())
                .andReturn();
        return body(result).path("taskId").asText();
    }

    private void approve(String token, String taskId) throws Exception {
        mockMvc.perform(post("/api/v1/interface-services/review-tasks/{taskId}/approve", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意执行\"}"))
                .andExpect(status().isOk());
    }

    private String firstServiceId(String token, String callerName) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/interface-services")
                        .param("callerName", callerName)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path(0).path("id").asText();
    }

    private String tokenWithRole(String username, String roleCode) throws Exception {
        User passwordAdministrator = createUser(username + "_password_admin");
        assignRole(passwordAdministrator, "SYS_ADMIN");
        User user = "SYS_ADMIN".equals(roleCode) ? passwordAdministrator : createUser(username);
        if (!"SYS_ADMIN".equals(roleCode)) {
            assignRole(user, roleCode);
        }
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(passwordAdministrator.id(), user.id(), "Password123"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device",
                                 "deviceName":"接口服务管理测试浏览器"}
                                """.formatted(user.username(), user.username())))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("accessToken").asText();
    }

    private User createUser(String username) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, "接口服务管理测试用户", null, UserType.PLATFORM));
    }

    private void assignRole(User user, String roleCode) {
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
