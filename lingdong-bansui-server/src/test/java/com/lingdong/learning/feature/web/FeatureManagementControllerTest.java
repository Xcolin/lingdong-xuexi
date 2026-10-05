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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FeatureManagementControllerTest {
    private static final String DB = "feature_management_" + java.util.UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:" + DB + ";MODE=MySQL;DB_CLOSE_DELAY=0;DATABASE_TO_LOWER=TRUE");
    }
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void detectsAbaAndRollsBackApproval() throws Exception {
        String admin=tokenWithRole("ft_aba_admin","SYS_ADMIN"), auditor=tokenWithRole("ft_aba_audit","SYS_AUDITOR");
        String id=submit(admin);
        jdbcTemplate.update("update sys_feature_toggle set status='DISABLED',version_no=version_no+1 where feature_code='STUDENT_CODE_LOGIN'");
        jdbcTemplate.update("update sys_feature_toggle set status='ENABLED',version_no=version_no+1 where feature_code='STUDENT_CODE_LOGIN'");
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/approve",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\"}"))
                .andExpect(status().isConflict());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("select status from sys_system_task where id=?",String.class,Long.valueOf(id))).isEqualTo("PENDING_REVIEW");
        jdbcTemplate.update("delete from sys_feature_toggle_change where task_id=?", Long.valueOf(id));
        jdbcTemplate.update("delete from sys_system_task where id=?", Long.valueOf(id));
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
    @Test
    void showsReviewableHistoryButRejectsExplicitDeniedSubmissionAndSelfReview() throws Exception {
        String admin=tokenWithRole("ft_scope_admin","SYS_ADMIN"), other=tokenWithRole("ft_scope_other","SYS_ADMIN");
        String id=submit(admin); submit(other);
        mockMvc.perform(get("/api/v1/feature-management/changes?pageSize=1").header("Authorization",bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/approve",id)
                .header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        Long adminId=jdbcTemplate.queryForObject("select submitted_by from sys_system_task where id=?",Long.class,Long.valueOf(id));
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(adminId,roleMapper.findByCode("SYS_AUDITOR").id(),null));
        jdbcTemplate.update("INSERT INTO sys_user_permission (id,user_id,permission_id,effect) SELECT ?,?,id,'DENY' FROM sys_permission WHERE permission_code='FEATURE_TOGGLE_MANAGE'", permissionIds.nextId(), adminId);
        sqlSession.clearCache();
        mockMvc.perform(post("/api/v1/feature-management/review-submissions").header("Authorization",bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"featureCode\":\"STUDENT_CODE_LOGIN\",\"targetStatus\":\"DISABLED\",\"expectedVersion\":\"0\",\"title\":\"测试\",\"description\":\"测试\",\"confirmed\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/approve",id)
                .header("Authorization",bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }
    @Test
    void requiresRejectionReasonAndRejectsCrossDomainTask() throws Exception {
        String admin=tokenWithRole("ft_reject_admin","SYS_ADMIN"), auditor=tokenWithRole("ft_reject_audit","SYS_AUDITOR");
        String id=submit(admin);
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/reject",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\" \"}"))
                .andExpect(status().isBadRequest());
        jdbcTemplate.update("update sys_system_task set task_type='CACHE_CLEAR' where id=?",Long.valueOf(id));
        sqlSession.clearCache();
        mockMvc.perform(post("/api/v1/feature-management/review-queue/{id}/reject",id)
                .header("Authorization",bearer(auditor)).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"驳回\"}"))
                .andExpect(status().isNotFound());
    }
    @Autowired private org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired private com.lingdong.learning.common.id.IdGenerator permissionIds;
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
