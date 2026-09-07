package com.lingdong.learning.cache.web;

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
class CacheManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void executesDirectOperationAndListsStringIdentifierHistory() throws Exception {
        String token = tokenWithRole("cache_api_admin", "SYS_ADMIN");

        MvcResult result = mockMvc.perform(post("/api/v1/cache-management/operations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cacheDomain":"DICTIONARY","operationType":"REFRESH",
                                 "impactDescription":"刷新数据字典缓存"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andReturn();
        String operationId = body(result).path("id").asText();

        mockMvc.perform(get("/api/v1/cache-management/operations")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')].cacheDomain".formatted(operationId))
                        .value("DICTIONARY"));
    }

    @Test
    void submitsAndApprovesHighRiskCacheOperationWithSeparatedRoles() throws Exception {
        String administratorToken = tokenWithRole("cache_api_submitter", "SYS_ADMIN");
        String auditorToken = tokenWithRole("cache_api_auditor", "SYS_AUDITOR");

        String taskId = submitHighRisk(administratorToken, "发布后全量清理");
        mockMvc.perform(get("/api/v1/cache-management/review-queue")
                        .header("Authorization", bearer(auditorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.taskId == '%s')].taskStatus".formatted(taskId))
                        .value("PENDING_REVIEW"));

        mockMvc.perform(post("/api/v1/cache-management/review-queue/{taskId}/approve", taskId)
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意执行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void rejectsHighRiskCacheOperationWithoutExecutingIt() throws Exception {
        String administratorToken = tokenWithRole("cache_api_reject_submitter", "SYS_ADMIN");
        String auditorToken = tokenWithRole("cache_api_reject_auditor", "SYS_AUDITOR");
        String taskId = submitHighRisk(administratorToken, "不满足变更窗口");

        mockMvc.perform(post("/api/v1/cache-management/review-queue/{taskId}/reject", taskId)
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"当前不在变更窗口\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.executedAt").doesNotExist());
    }

    @Test
    void revokesAllActiveDeviceSessionsAfterUserSessionClearIsApproved() throws Exception {
        String administratorToken = tokenWithRole("cache_api_session_submitter", "SYS_ADMIN");
        String auditorToken = tokenWithRole("cache_api_session_auditor", "SYS_AUDITOR");
        String taskId = submitHighRisk(administratorToken, "USER_SESSION", "安全事件后撤销全部活动会话");
        Integer activeBeforeApproval = jdbcTemplate.queryForObject(
                "select count(*) from auth_device_session where status = 'ACTIVE'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(activeBeforeApproval).isGreaterThanOrEqualTo(2);

        mockMvc.perform(post("/api/v1/cache-management/review-queue/{taskId}/approve", taskId)
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意撤销全部活动会话\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        Integer activeAfterApproval = jdbcTemplate.queryForObject(
                "select count(*) from auth_device_session where status = 'ACTIVE'", Integer.class);
        org.assertj.core.api.Assertions.assertThat(activeAfterApproval).isZero();
    }

    @Test
    void rejectsMissingDynamicPermissionAndDisabledFeature() throws Exception {
        String ordinaryToken = tokenWithRole("cache_api_ordinary", "PARENT");
        mockMvc.perform(get("/api/v1/cache-management/operations")
                        .header("Authorization", bearer(ordinaryToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String administratorToken = tokenWithRole("cache_api_disabled", "SYS_ADMIN");
        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'CACHE_MANAGEMENT' and scope_key = 'GLOBAL'
                """);
        mockMvc.perform(get("/api/v1/cache-management/operations")
                        .header("Authorization", bearer(administratorToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void publishesCacheManagementPathsInAuthenticatedOpenApiJson() throws Exception {
        String token = tokenWithRole("cache_api_openapi", "SYS_ADMIN");
        mockMvc.perform(get("/api/v1/openapi").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/cache-management/operations']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cache-management/review-submissions']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cache-management/review-queue']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cache-management/review-queue/{taskId}/approve']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cache-management/review-queue/{taskId}/reject']").exists());
    }

    private String submitHighRisk(String token, String description) throws Exception {
        return submitHighRisk(token, "ALL", description);
    }

    private String submitHighRisk(String token, String cacheDomain, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cache-management/review-submissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cacheDomain":"%s","operationType":"CLEAR","title":"高风险缓存清除",
                                 "description":"%s","confirmed":true}
                                """.formatted(cacheDomain, description)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").isString())
                .andReturn();
        return body(result).path("taskId").asText();
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
                                 "deviceName":"缓存管理测试浏览器"}
                                """.formatted(user.username(), user.username())))
                .andExpect(status().isOk())
                .andReturn();
        return body(result).path("accessToken").asText();
    }

    private User createUser(String username) {
        return userAccessApplicationService.createUser(
                new CreateUserCommand(username, "缓存管理测试用户", null, UserType.PLATFORM));
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
