package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.application.CreateOrganizationCommand;
import com.lingdong.learning.organization.application.OrganizationApplicationService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganizationChangeControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private OrganizationApplicationService organizationApplicationService;
    @Autowired private OrganizationMapper organizationMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void editsEnablesAndApprovesOrganizationChangesWithSeparatedRoles() throws Exception {
        User administrator = createUserWithRole("org_change_api_admin", "组织变更接口管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("org_change_api_auditor", "组织变更接口审核员", "SYS_AUDITOR");
        setPasswords(administrator, auditor);
        String adminToken = loginAccessToken(administrator.username());
        String auditorToken = loginAccessToken(auditor.username());
        Organization editable = createRegion("REGION_CHANGE_API_EDIT", "接口待编辑区域");

        mockMvc.perform(put("/api/v1/organizations/{organizationId}", editable.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"接口编辑后区域","sortOrder":30,"versionNo":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(editable.id().toString()))
                .andExpect(jsonPath("$.name").value("接口编辑后区域"))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.effectiveStatus").value("ENABLED"));

        jdbcTemplate.update("""
                UPDATE sys_organization SET status = 'DISABLED', effective_status = 'DISABLED'
                WHERE id = ?
                """, editable.id());
        mockMvc.perform(post("/api/v1/organizations/{organizationId}/enable", editable.id())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.versionNo").value(3));

        Organization pendingDisable = createRegion("REGION_CHANGE_API_DISABLE", "接口待停用区域");
        MvcResult requestResult = mockMvc.perform(post("/api/v1/organization-changes")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId":"%s",
                                  "changeType":"DISABLE",
                                  "expectedVersion":1,
                                  "reason":"接口审核停用"
                                }
                                """.formatted(pendingDisable.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.changeId").isString())
                .andExpect(jsonPath("$.taskId").isString())
                .andExpect(jsonPath("$.taskStatus").value("PENDING_REVIEW"))
                .andReturn();
        String taskId = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .path("taskId").asText();

        mockMvc.perform(get("/api/v1/organization-changes")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.taskId == '%s')]".formatted(taskId)).exists());
        mockMvc.perform(get("/api/v1/organization-changes")
                        .header("Authorization", bearer(auditorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.taskId == '%s')]".formatted(taskId)).exists());
        mockMvc.perform(post("/api/v1/organization-changes/{taskId}/approve", taskId)
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskStatus").value("EFFECTIVE"))
                .andExpect(jsonPath("$.executionStatus").value("APPLIED"));
        assertThat(organizationMapper.findById(pendingDisable.id()).status())
                .isEqualTo(OrganizationStatus.DISABLED);

        mockMvc.perform(post("/api/v1/organization-changes")
                        .header("Authorization", bearer(auditorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"organizationId":"%s","changeType":"DELETE","expectedVersion":1,"reason":"越权提交"}
                                """.formatted(pendingDisable.id())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/organization-changes/{taskId}/approve", taskId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"越权审核\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void blocksOrganizationApisAndExposesDisabledWebCapability() throws Exception {
        User administrator = createUserWithRole("org_change_api_switch", "组织功能开关管理员", "SYS_ADMIN");
        setPasswords(administrator);
        String token = loginAccessToken(administrator.username());
        jdbcTemplate.update("""
                UPDATE sys_feature_toggle SET status = 'DISABLED'
                WHERE feature_code = 'ORGANIZATION_MANAGEMENT' AND scope_key = 'GLOBAL'
                """);
        try {
            mockMvc.perform(get("/api/v1/organizations")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
            mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.organizationManagementEnabled").value(false));
        } finally {
            jdbcTemplate.update("""
                    UPDATE sys_feature_toggle SET status = 'ENABLED'
                    WHERE feature_code = 'ORGANIZATION_MANAGEMENT' AND scope_key = 'GLOBAL'
                    """);
        }
    }

    private Organization createRegion(String code, String name) {
        return organizationApplicationService.createOrganization(
                new CreateOrganizationCommand(code, name, "REGION", null, 10));
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private void setPasswords(User administrator, User... users) {
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(administrator.id(), administrator.id(), "Password123"));
        for (User user : users) {
            authenticationApplicationService.setPlatformUserPassword(
                    new SetPlatformUserPasswordCommand(administrator.id(), user.id(), "Password123"));
        }
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"组织变更测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
