package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.student.application.IssuedStudentCredential;
import com.lingdong.learning.student.application.StudentIdentityProvisioningService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentQrAuthenticationControllerTest {
    private static final long STUDENT_ID = 1_874_244_142_494_646_540L;
    private static final long RELATION_ID = 1_874_244_142_494_646_541L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccessApplicationService userAccessService;
    @Autowired private AuthenticationApplicationService authenticationService;
    @Autowired private StudentIdentityProvisioningService provisioningService;
    @Autowired private StudentMapper studentMapper;
    @Autowired private ParentStudentMapper parentStudentMapper;
    @Autowired private RoleMapper roleMapper;
    @Autowired private FeatureToggleMapper featureToggleMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User primaryParent;
    private User unrelatedParent;
    private IssuedStudentCredential credential;

    @BeforeEach
    void setUp() {
        featureToggleMapper.updateGlobalStatus("STUDENT_QR_LOGIN", FeatureStatus.ENABLED);
        User systemAdministrator = createUserWithRole("qr_sys_admin", "扫码测试系统管理员", "SYS_ADMIN");
        primaryParent = createUserWithRole("qr_parent", "扫码测试主家长", "PARENT");
        unrelatedParent = createUserWithRole("qr_other_parent", "扫码测试其他家长", "PARENT");
        setPassword(systemAdministrator, primaryParent);
        setPassword(systemAdministrator, unrelatedParent);
        credential = provisioningService.issue("扫码登录学生");
        studentMapper.insert(Student.create(STUDENT_ID, "扫码登录学生", "G3", credential.studentUserId()));
        parentStudentMapper.insertPrimary(RELATION_ID, primaryParent.id(), STUDENT_ID);
    }

    @Test
    void primaryParentIssuesTicketAndStudentUsesItOnlyOnce() throws Exception {
        String parentToken = loginAccessToken(primaryParent.username());
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "MINIAPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentQrLoginEnabled").value(true));

        String qrContent = issueQrTicket(parentToken);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/student-sessions/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrLoginBody(qrContent, credential.plainLoginCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.studentAccount").value(credential.studentAccount()))
                .andReturn();
        assertThat(objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("sessionId").asText()).hasSize(19);

        mockMvc.perform(post("/api/v1/auth/student-sessions/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrLoginBody(qrContent, credential.plainLoginCode())))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("STUDENT_QR_TICKET_INVALID"));
    }

    @Test
    void wrongCodeConsumesTicketAndUnrelatedParentCannotIssue() throws Exception {
        mockMvc.perform(post("/api/v1/students/{id}/login-qr-tickets", STUDENT_ID)
                        .header("Authorization", "Bearer " + loginAccessToken(unrelatedParent.username())))
                .andExpect(status().isNotFound());

        String qrContent = issueQrTicket(loginAccessToken(primaryParent.username()));
        mockMvc.perform(post("/api/v1/auth/student-sessions/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrLoginBody(qrContent, "9999")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STUDENT_AUTH_FAILED"));

        mockMvc.perform(post("/api/v1/auth/student-sessions/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrLoginBody(qrContent, credential.plainLoginCode())))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("STUDENT_QR_TICKET_INVALID"));
    }

    @Test
    void disabledFeatureRejectsIssuingAndQrLogin() throws Exception {
        String parentToken = loginAccessToken(primaryParent.username());
        String qrContent = issueQrTicket(parentToken);
        featureToggleMapper.updateGlobalStatus("STUDENT_QR_LOGIN", FeatureStatus.DISABLED);

        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentQrLoginEnabled").value(false));
        mockMvc.perform(post("/api/v1/students/{id}/login-qr-tickets", STUDENT_ID)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        mockMvc.perform(post("/api/v1/auth/student-sessions/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrLoginBody(qrContent, credential.plainLoginCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void expiredTicketIsRejectedAndFreshTicketCanIssueQrCaptcha() throws Exception {
        String parentToken = loginAccessToken(primaryParent.username());
        String expiredQrContent = issueQrTicket(parentToken);
        jdbcTemplate.update("update auth_student_qr_ticket set expires_at = CURRENT_TIMESTAMP where status = 'ACTIVE'");

        mockMvc.perform(post("/api/v1/auth/student-sessions/qr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(qrLoginBody(expiredQrContent, credential.plainLoginCode())))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("STUDENT_QR_TICKET_INVALID"));

        String freshQrContent = issueQrTicket(parentToken);
        mockMvc.perform(post("/api/v1/auth/student-qr-captchas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"qrContent":"%s","deviceId":"qr-student-device"}
                                """.formatted(freshQrContent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.imageBase64").value("data:image/png;base64,dGVzdA=="));
    }

    private String issueQrTicket(String parentToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/students/{id}/login-qr-tickets", STUDENT_ID)
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketId").isString())
                .andExpect(jsonPath("$.qrContent").value(org.hamcrest.Matchers.startsWith(
                        "lingdong-learning://student-login?ticket=")))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("qrContent").asText();
    }

    private String qrLoginBody(String qrContent, String loginCode) throws Exception {
        JsonNode body = objectMapper.readTree("{}").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body)
                .put("qrContent", qrContent)
                .put("loginCode", loginCode)
                .put("deviceId", "qr-student-device")
                .put("deviceName", "扫码登录测试设备");
        return objectMapper.writeValueAsString(body);
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessService.createUser(new CreateUserCommand(username, displayName, null, UserType.PLATFORM));
        Role role = roleMapper.findByCode(roleCode);
        userAccessService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }

    private void setPassword(User systemAdministrator, User targetUser) {
        authenticationService.setPlatformUserPassword(new SetPlatformUserPasswordCommand(
                systemAdministrator.id(), targetUser.id(), "Password123"));
    }

    private String loginAccessToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"Password123","deviceId":"%s-device","deviceName":"扫码测试浏览器"}
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
