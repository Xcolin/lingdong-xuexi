package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.student.application.IssuedStudentCredential;
import com.lingdong.learning.student.application.StudentIdentityProvisioningService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class StudentAuthenticationControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StudentIdentityProvisioningService provisioningService;
    @Autowired private StudentMapper studentMapper;
    @Autowired private FeatureToggleMapper featureToggleMapper;

    private IssuedStudentCredential issued;
    private String deviceId;

    @BeforeEach
    void createStudent() {
        featureToggleMapper.updateGlobalStatus("STUDENT_CODE_LOGIN", FeatureStatus.ENABLED);
        issued = provisioningService.issue("学生HTTP登录测试");
        deviceId = "http-device-" + issued.studentUserId();
        studentMapper.insert(Student.create(
                1_874_244_142_494_646_530L, "学生HTTP登录测试", "G4", issued.studentUserId()));
    }

    @Test
    void exposesCapabilitiesCaptchaAndStudentCodeLoginWithoutExistingSession() throws Exception {
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "MINIAPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCodeLoginEnabled").value(true))
                .andExpect(jsonPath("$.learningTaskManagementEnabled").value(true));

        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learningTaskManagementEnabled").value(true));

        mockMvc.perform(post("/api/v1/auth/student-captchas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"studentAccount":"%s","deviceId":"%s"}
                                """.formatted(issued.studentAccount(), deviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeId").isString())
                .andExpect(jsonPath("$.imageBase64").value("data:image/png;base64,dGVzdA=="))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/student-sessions/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(issued.plainLoginCode(), null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isString())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();
        assertThat(objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("sessionId").asText()).hasSize(19);
    }

    @Test
    void returnsStudentSpecificErrorsAndRejectsLoginWhenFeatureIsDisabled() throws Exception {
        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/student-sessions/code")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("9999", null, null)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("STUDENT_AUTH_FAILED"));
        }
        mockMvc.perform(post("/api/v1/auth/student-sessions/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("9999", null, null)))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("CAPTCHA_REQUIRED"));

        featureToggleMapper.updateGlobalStatus("STUDENT_CODE_LOGIN", FeatureStatus.DISABLED);
        mockMvc.perform(get("/api/v1/public/capabilities").param("client", "MINIAPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentCodeLoginEnabled").value(false))
                .andExpect(jsonPath("$.learningTaskManagementEnabled").value(true));
        mockMvc.perform(post("/api/v1/auth/student-sessions/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(issued.plainLoginCode(), null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    private String loginBody(String loginCode, String challengeId, String captchaAnswer) throws Exception {
        JsonNode body = objectMapper.readTree("{}")
                .deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) body)
                .put("studentAccount", issued.studentAccount())
                .put("loginCode", loginCode)
                .put("deviceId", deviceId)
                .put("deviceName", "学生HTTP测试设备");
        if (challengeId != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("captchaChallengeId", challengeId);
        }
        if (captchaAnswer != null) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("captchaAnswer", captchaAnswer);
        }
        return objectMapper.writeValueAsString(body);
    }
}
