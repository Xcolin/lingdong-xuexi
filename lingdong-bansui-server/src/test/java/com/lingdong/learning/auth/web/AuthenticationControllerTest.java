package com.lingdong.learning.auth.web;

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
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationControllerTest {
    @Test
    void pagesSecurityEventsWithUserScopeAndWholeUnreadWarningCount() throws Exception {
        String suffix=Long.toUnsignedString(System.nanoTime());
        User owner=createUser("event_page_"+suffix,"安全事件分页用户"),other=createUser("event_other_"+suffix,"范围外事件用户");
        for(User user:List.of(owner,other)) jdbcTemplate.update("UPDATE sys_user SET password_hash=? WHERE id=?",passwordEncoder.encode("Password123"),user.id());
        login(owner.username(),"event-one","中文提醒一");
        JsonNode current=login(owner.username(),"event-two","中文提醒二");
        login(other.username(),"event-other","范围外提醒");
        String header=bearer(current.path("accessToken").asText());
        JsonNode first=objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/security-events/page").header("Authorization",header).param("pageSize","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.unreadWarningTotal").value(2))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].deviceFingerprintHash").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        JsonNode second=objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/security-events/page").header("Authorization",header).param("page","2").param("pageSize","1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(first.path("items").get(0).path("id").asText()).isNotEqualTo(second.path("items").get(0).path("id").asText());
        mockMvc.perform(post("/api/v1/auth/security-events/{id}/read",first.path("items").get(0).path("id").asText()).header("Authorization",header)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/security-events/page").header("Authorization",header).param("unreadOnly","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.unreadWarningTotal").value(1));
        mockMvc.perform(get("/api/v1/auth/security-events").header("Authorization",header)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/auth/security-events/page")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/security-events/page").header("Authorization",header).param("pageSize","101")).andExpect(status().isBadRequest());
    }
    @Test
    void pagesOnlyCurrentUsersActiveDevicesWithoutChangingLegacyList() throws Exception {
        String suffix = Long.toUnsignedString(System.nanoTime());
        User owner = createUser("device_page_" + suffix, "分页设备核验用户");
        User other = createUser("device_other_" + suffix, "范围外设备用户");
        for (User user : List.of(owner, other)) {
            jdbcTemplate.update("UPDATE sys_user SET password_hash=? WHERE id=?",
                    passwordEncoder.encode("Password123"), user.id());
        }
        JsonNode first = login(owner.username(), "page-one", "中文设备一");
        JsonNode current = login(owner.username(), "page-two", "中文设备二");
        login(other.username(), "other-device", "范围外设备");
        String header = bearer(current.path("accessToken").asText());
        JsonNode pageOne = objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/devices/page")
                .header("Authorization", header).param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$.items[0].deviceId").doesNotExist())
                .andReturn().getResponse().getContentAsString());
        JsonNode pageTwo = objectMapper.readTree(mockMvc.perform(get("/api/v1/auth/devices/page")
                .header("Authorization", header).param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(pageOne.path("items").get(0).path("id").asText())
                .isNotEqualTo(pageTwo.path("items").get(0).path("id").asText());
        mockMvc.perform(get("/api/v1/auth/devices/page").header("Authorization", header)
                .param("page", "3").param("pageSize", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/auth/devices/page").header("Authorization", header)
                .param("page", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/devices").header("Authorization", header))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/auth/devices/page")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/devices/page").header("Authorization", header)
                .param("pageSize", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/auth/devices/page").header("Authorization", header)
                .param("page", "2147483647").param("pageSize", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        jdbcTemplate.update("UPDATE auth_device_session SET refresh_expires_at=? WHERE id=?",
                java.time.LocalDateTime.now().minusDays(1), first.path("sessionId").asLong());
        mockMvc.perform(get("/api/v1/auth/devices/page").header("Authorization", header))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].current").value(true));
        jdbcTemplate.update("UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code='ACCOUNT_SECURITY_MANAGEMENT'");
        try {
            mockMvc.perform(get("/api/v1/auth/devices/page").header("Authorization", header))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        } finally {
            jdbcTemplate.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='ACCOUNT_SECURITY_MANAGEMENT'");
        }
        assertThat(first.path("sessionId").asText()).isNotBlank();
    }
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationAdminMapper organizationAdminMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private IdGenerator idGenerator;
    @Autowired private org.springframework.context.ApplicationContext applicationContext;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void doesNotInstallSpringSecuritysGeneratedDefaultUser() {
        Map<String, UserDetailsService> userDetailsServices = applicationContext.getBeansOfType(UserDetailsService.class);

        assertThat(userDetailsServices).isEmpty();
    }

    @Test
    void rejectsAnonymousAccessToCurrentUserWithUnifiedAuthenticationError() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void keepsHealthCheckPublicWhenOtherApiResourcesRequireAuthentication() throws Exception {
        // 免认证可达性：测试环境无 Redis，健康探测返回 503 DEGRADED 而非认证错误，即证明端点公开。
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }

    @Test
    void authenticatesPasswordLoginAndRevokesTheCurrentTokenAfterLogout() throws Exception {
        User administrator = createUserWithRole("auth_web_admin", "认证接口管理员", "SYS_ADMIN");
        User platformUser = createUser("auth_web_user", "认证接口用户");
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), platformUser.id(), "Password123"
        ));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"auth_web_user","password":"Password123","deviceId":"web-auth-001","deviceName":"认证接口浏览器"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
        JsonNode loginResponse = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginResponse.path("accessToken").asText();
        String sessionId = loginResponse.path("sessionId").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isString())
                .andExpect(jsonPath("$.userId").value(platformUser.id().toString()))
                .andExpect(jsonPath("$.username").value("auth_web_user"))
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.permissionCodes").isArray());
        mockMvc.perform(get("/api/v1/auth/devices").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].id").value(sessionId))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].deviceId").doesNotExist())
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].refreshToken").doesNotExist());

        mockMvc.perform(delete("/api/v1/auth/sessions/current").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        assertThat(loginResponse.path("password").isMissingNode()).isTrue();
    }

    @Test
    void managesOwnDevicesAndSecurityEventsWithFeatureAndPrivacyBoundaries() throws Exception {
        User administrator = createUserWithRole("auth_security_api_admin", "安全接口管理员", "SYS_ADMIN");
        User owner = createUser("auth_security_api_owner", "安全接口用户");
        User other = createUser("auth_security_api_other", "其他安全接口用户");
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), owner.id(), "Password123"));
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                administrator.id(), other.id(), "Password123"));
        // bf34c6a 管理员设密后要求首登改密，业务测试直接清除首登改密标记（security-events 不在首登改密豁免端点内）
        jdbcTemplate.update("UPDATE auth_initial_password_state SET change_required=false WHERE user_id IN (?, ?)", owner.id(), other.id());

        JsonNode firstLogin = login("auth_security_api_owner", "security-web-1", "办公浏览器");
        JsonNode currentLogin = login("auth_security_api_owner", "security-web-2", "家庭浏览器");
        JsonNode otherLogin = login("auth_security_api_other", "security-other", "其他浏览器");
        String token = currentLogin.path("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/devices").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + currentLogin.path("sessionId").asText() + "')].current").value(true))
                .andExpect(jsonPath("$[0].deviceId").doesNotExist());

        MvcResult eventsResult = mockMvc.perform(get("/api/v1/auth/security-events")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].eventType").value("NEW_DEVICE_LOGIN"))
                .andExpect(jsonPath("$[0].riskLevel").value("WARNING"))
                .andExpect(jsonPath("$[0].deviceFingerprintHash").doesNotExist())
                .andExpect(jsonPath("$[0].eventScopeKey").doesNotExist())
                .andReturn();
        String eventId = objectMapper.readTree(eventsResult.getResponse().getContentAsString()).get(0).path("id").asText();
        mockMvc.perform(post("/api/v1/auth/security-events/{eventId}/read", eventId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/security-events/read-all")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/security-events").param("unreadOnly", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(delete("/api/v1/auth/devices/{sessionId}", otherLogin.path("sessionId").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/auth/devices/{sessionId}", firstLogin.path("sessionId").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        jdbcTemplate.update("update sys_feature_toggle set status = 'DISABLED' where feature_code = ?", "ACCOUNT_SECURITY_MANAGEMENT");
        try {
            mockMvc.perform(get("/api/v1/auth/devices").header("Authorization", bearer(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mockMvc.perform(get("/api/v1/auth/security-events").header("Authorization", bearer(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mockMvc.perform(delete("/api/v1/auth/sessions/current").header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());
        } finally {
            jdbcTemplate.update("update sys_feature_toggle set status = 'ENABLED' where feature_code = ?", "ACCOUNT_SECURITY_MANAGEMENT");
        }
    }

    @Test
    void exposesOrganizationMiniappPasswordLoginAndClientSpecificCapability() throws Exception {
        User administrator = userAccessApplicationService.createUser(new CreateUserCommand(
                "auth_org_mini_api", "机构小程序接口管理员", null, UserType.ORGANIZATION));
        assertThat(userMapper.updatePasswordHash(
                administrator.id(), passwordEncoder.encode("ValidPass123!"))).isEqualTo(1);
        Role role = roleMapper.findByCode("ORG_ADMIN");
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(administrator.id(), role.id(), null));
        Organization organization = organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(
                        "AUTH_ORG_MINI_API", "机构小程序接口学校", "SCHOOL", null, 10));
        assertThat(organizationAdminMapper.insert(
                idGenerator.nextId(), administrator.id(), organization.id())).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/organization-sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"auth_org_mini_api","password":"ValidPass123!",
                                 "deviceId":"organization-api-device","deviceName":"机构管理员手机"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/organization-sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":"ValidPass123!",
                                 "deviceId":"organization-api-device","deviceName":"机构管理员手机"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "MINIAPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationMiniappAuthEnabled").value(true))
                .andExpect(jsonPath("$.studentOrganizationRelationshipEnabled").value(true));
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationMiniappAuthEnabled").value(false))
                .andExpect(jsonPath("$.studentOrganizationRelationshipEnabled").value(true));

        jdbcTemplate.update("""
                update sys_feature_toggle set status = 'DISABLED'
                where feature_code = 'ORGANIZATION_MINIAPP_AUTH'
                """);
        try {
            mockMvc.perform(post("/api/v1/auth/organization-sessions/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"auth_org_mini_api","password":"ValidPass123!",
                                     "deviceId":"organization-api-disabled","deviceName":"机构管理员手机"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        } finally {
            jdbcTemplate.update("""
                    update sys_feature_toggle set status = 'ENABLED'
                    where feature_code = 'ORGANIZATION_MINIAPP_AUTH'
                    """);
        }
    }

    private JsonNode login(String username, String deviceId, String deviceName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Password123",
                                "deviceId", deviceId,
                                "deviceName", deviceName))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = createUser(username, displayName);
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private User createUser(String username, String displayName) {
        return userAccessApplicationService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
    }
}
