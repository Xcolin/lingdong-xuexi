package com.lingdong.learning.student.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentCredentialManagementControllerTest {
    private static final long HISTORICAL_STUDENT_ID = 1_874_244_142_494_646_510L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserAccessApplicationService userAccessApplicationService;
    @Autowired private AuthenticationApplicationService authenticationApplicationService;
    @Autowired private RoleMapper roleMapper;
    @Autowired private ParentStudentMapper parentStudentMapper;
    @Autowired private UserMapper userMapper;

    @Test
    void primaryParentInitializesAndResetsHistoricalStudentCredentialWithinObjectScope() throws Exception {
        User systemAdministrator = createUserWithRole(
                "credential_sys_admin", "凭证测试系统管理员", "SYS_ADMIN");
        User primaryParent = createUserWithRole("credential_parent", "凭证测试主家长", "PARENT");
        User unrelatedParent = createUserWithRole("credential_other_parent", "凭证测试其他家长", "PARENT");
        setPassword(systemAdministrator, primaryParent);
        setPassword(systemAdministrator, unrelatedParent);
        createHistoricalStudent(primaryParent.id());

        mockMvc.perform(post("/api/v1/students/{id}/credentials/initialize", HISTORICAL_STUDENT_ID)
                        .header("Authorization", "Bearer " + loginAccessToken(unrelatedParent.username())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        MvcResult initializedResult = mockMvc.perform(
                        post("/api/v1/students/{id}/credentials/initialize", HISTORICAL_STUDENT_ID)
                                .header("Authorization", "Bearer " + loginAccessToken(primaryParent.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentAccount").isString())
                .andExpect(jsonPath("$.loginCode").isString())
                .andReturn();
        JsonNode initialized = objectMapper.readTree(initializedResult.getResponse().getContentAsString());
        String studentAccount = initialized.path("studentAccount").asText();
        String initialCode = initialized.path("loginCode").asText();
        assertThat(studentAccount).matches("\\d{8}");
        assertThat(initialCode).matches("\\d{4}");

        mockMvc.perform(post("/api/v1/students/{id}/credentials/initialize", HISTORICAL_STUDENT_ID)
                        .header("Authorization", "Bearer " + loginAccessToken(primaryParent.username())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        User studentUser = userMapper.findByUsername(studentAccount);
        insertActiveStudentSession(studentUser.id());
        String oldHash = jdbcTemplate.queryForObject(
                "select code_hash from auth_student_credential where student_user_id = ?",
                String.class, studentUser.id());

        MvcResult resetResult = mockMvc.perform(
                        post("/api/v1/students/{id}/login-code-resets", HISTORICAL_STUDENT_ID)
                                .header("Authorization", "Bearer " + loginAccessToken(primaryParent.username())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentAccount").value(studentAccount))
                .andExpect(jsonPath("$.loginCode").isString())
                .andReturn();
        String resetCode = objectMapper.readTree(resetResult.getResponse().getContentAsString()).path("loginCode").asText();
        String newHash = jdbcTemplate.queryForObject(
                "select code_hash from auth_student_credential where student_user_id = ?",
                String.class, studentUser.id());
        String sessionStatus = jdbcTemplate.queryForObject(
                "select status from auth_device_session where user_id = ?", String.class, studentUser.id());

        assertThat(resetCode).matches("\\d{4}");
        assertThat(newHash).isNotEqualTo(oldHash).doesNotContain(resetCode);
        assertThat(sessionStatus).isEqualTo("REVOKED");
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private void setPassword(User systemAdministrator, User targetUser) {
        authenticationApplicationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                systemAdministrator.id(), targetUser.id(), "Password123"));
    }

    private void createHistoricalStudent(Long primaryParentId) {
        jdbcTemplate.update("""
                insert into edu_student (id, student_name, grade_code, student_user_id, status)
                values (?, ?, ?, null, 'ENABLED')
                """, HISTORICAL_STUDENT_ID, "历史家庭学生", "G2");
        parentStudentMapper.insertPrimary(1_874_244_142_494_646_511L, primaryParentId, HISTORICAL_STUDENT_ID);
    }

    private void insertActiveStudentSession(Long studentUserId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                insert into auth_device_session (
                    id, user_id, client_type, device_id, device_name, access_token_hash, refresh_token_hash,
                    access_expires_at, refresh_expires_at, status, last_active_at
                ) values (?, ?, 'MINIAPP', 'student-device', '学生测试设备', ?, ?, ?, ?, 'ACTIVE', ?)
                """,
                1_874_244_142_494_646_512L, studentUserId, "a".repeat(64), "b".repeat(64),
                now.plusMinutes(30), now.plusDays(7), now);
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"凭证管理测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
