package com.lingdong.learning.feature.web;

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
class FeatureManagementControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void submitsSnapshotAndApprovesWithStringIdentifiers() throws Exception {
        String admin=tokenWithRole("ft_admin","SYS_ADMIN"), auditor=tokenWithRole("ft_audit","SYS_AUDITOR");
        mockMvc.perform(get("/api/v1/feature-management/toggles").header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].versionNo").isString());
        String id=submit(admin);
        mockMvc.perform(get("/api/v1/feature-management/review-queue").header("Authorization",bearer(auditor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].taskId").isString())
                .andExpect(jsonPath("$.items[0].beforeStatus").value("ENABLED"));
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/approve",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskStatus").value("EFFECTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("DISABLED"));
    }
    @Test
    void detectsAbaAndRollsBackApproval() throws Exception {
        String admin=tokenWithRole("ft_aba_admin","SYS_ADMIN"), auditor=tokenWithRole("ft_aba_audit","SYS_AUDITOR");
        String id=submit(admin);
        jdbcTemplate.update("update sys_feature_toggle set status='DISABLED',version_no=version_no+1 where feature_code='STUDENT_CODE_LOGIN'");
        jdbcTemplate.update("update sys_feature_toggle set status='ENABLED',version_no=version_no+1 where feature_code='STUDENT_CODE_LOGIN'");
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/approve",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\"}"))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select status from sys_system_task where id=?",String.class,Long.valueOf(id))).isEqualTo("PENDING_REVIEW");
    }
    @Test
    void rejectsLegacySnapshotAndAllowsRejection() throws Exception {
        String admin=tokenWithRole("ft_old_admin","SYS_ADMIN"), auditor=tokenWithRole("ft_old_audit","SYS_AUDITOR");
        String id=submit(admin);
        jdbcTemplate.update("update sys_feature_toggle_change set before_status=null,base_version=null where task_id=?",Long.valueOf(id));
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/approve",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/reject",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"请重新提交\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.taskStatus").value("REJECTED"));
    }
    @Test
    void deniesReadAfterDynamicPermissionRevoked() throws Exception {
        String token=tokenWithRole("ft_revoke","SYS_ADMIN");
        jdbcTemplate.update("delete from sys_role_permission where permission_id=(select id from sys_permission where permission_code='FEATURE_TOGGLE_READ')");
        mockMvc.perform(get("/api/v1/feature-management/toggles").header("Authorization",bearer(token))).andExpect(status().isForbidden());
    }
    private String submit(String token) throws Exception {
        Long version=jdbcTemplate.queryForObject("select version_no from sys_feature_toggle where feature_code='STUDENT_CODE_LOGIN'",Long.class);
        return body(mockMvc.perform(post("/api/v1/feature-management/review-submissions")
                .header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"featureCode\":\"STUDENT_CODE_LOGIN\",\"targetStatus\":\"DISABLED\",\"expectedVersion\":\""+version+"\",\"title\":\"停用登录\",\"description\":\"维护调整\",\"confirmed\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isString()).andReturn()).path("taskId").asText();
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
