package com.lingdong.learning.auth.web;

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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

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
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private org.springframework.context.ApplicationContext applicationContext;

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
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
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
                .andExpect(jsonPath("$.sessionId").value(sessionId));
        mockMvc.perform(get("/api/v1/auth/devices").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].id").value(sessionId))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].refreshToken").doesNotExist());

        mockMvc.perform(delete("/api/v1/auth/sessions/current").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        assertThat(loginResponse.path("password").isMissingNode()).isTrue();
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
