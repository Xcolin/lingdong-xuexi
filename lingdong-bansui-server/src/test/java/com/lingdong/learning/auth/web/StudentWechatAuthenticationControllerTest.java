package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.infrastructure.memory.FixedTestParentSmsCodeGenerator;
import com.lingdong.learning.auth.infrastructure.memory.InMemoryParentSmsVerificationStore;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.student.application.IssuedStudentCredential;
import com.lingdong.learning.student.application.StudentIdentityProvisioningService;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
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
class StudentWechatAuthenticationControllerTest {
    private static final String MOBILE = "13800138026";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FeatureToggleMapper featureToggleMapper;
    @Autowired private InMemoryParentSmsVerificationStore smsStore;
    @Autowired private StudentIdentityProvisioningService provisioningService;
    @Autowired private StudentMapper studentMapper;
    @Autowired private ParentStudentMapper relationshipMapper;
    @Autowired private IdGenerator idGenerator;

    @BeforeEach
    void enableFeatures() {
        smsStore.clear();
        featureToggleMapper.updateGlobalStatus("PARENT_PHONE_AUTH", FeatureStatus.ENABLED);
        featureToggleMapper.updateGlobalStatus("STUDENT_WECHAT_AUTH", FeatureStatus.ENABLED);
    }

    @Test
    void bindsLogsInListsAndLetsOnlyPrimaryParentUnbindWithoutLeakingIdentity() throws Exception {
        String parentToken = registerParentAndCompleteOnboarding();
        Long parentUserId = jdbcTemplate.queryForObject(
                "select id from sys_user where mobile=?", Long.class, MOBILE);
        IssuedStudentCredential issued = createStudent("微信绑定学生", parentUserId);

        MvcResult exchangeResult = exchangeWechat()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingRequired").value(true))
                .andExpect(jsonPath("$.bindingTicket").isString())
                .andReturn();
        String exchangeBody = exchangeResult.getResponse().getContentAsString();
        assertThat(exchangeBody).doesNotContain("openid-test").doesNotContain("session-key-test");
        String bindingTicket = objectMapper.readTree(exchangeBody).path("bindingTicket").asText();

        MvcResult codeResult = mockMvc.perform(post("/api/v1/auth/student-wechat-binding-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bindingTicket":"%s",
                                  "studentAccount":"%s",
                                  "loginCode":"%s",
                                  "deviceId":"student-wechat-device",
                                  "deviceName":"学生微信小程序"
                                }
                                """.formatted(bindingTicket, issued.studentAccount(), issued.plainLoginCode())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationTicket").isString())
                .andExpect(jsonPath("$.maskedMobile").value("138****8026"))
                .andReturn();
        String codeBody = codeResult.getResponse().getContentAsString();
        assertThat(codeBody).doesNotContain(MOBILE)
                .doesNotContain(FixedTestParentSmsCodeGenerator.CODE)
                .doesNotContain("openid-test");
        String verificationTicket = objectMapper.readTree(codeBody).path("verificationTicket").asText();

        mockMvc.perform(post("/api/v1/auth/student-wechat-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "verificationTicket":"%s",
                                  "smsCode":"%s",
                                  "deviceId":"student-wechat-device",
                                  "deviceName":"学生微信小程序"
                                }
                                """.formatted(verificationTicket, FixedTestParentSmsCodeGenerator.CODE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.studentAccount").value(issued.studentAccount()));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_student_wechat_binding", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_student_wechat_binding_audit where event_type='BIND'",
                Integer.class)).isEqualTo(1);

        exchangeWechat()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingRequired").value(false))
                .andExpect(jsonPath("$.bindingTicket").doesNotExist())
                .andExpect(jsonPath("$.session.accessToken").isString())
                .andExpect(jsonPath("$.session.studentAccount").value(issued.studentAccount()));

        mockMvc.perform(get("/api/v1/student-wechat-bindings")
                        .header("Authorization", "Bearer " + parentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("微信绑定学生"))
                .andExpect(jsonPath("$[0].studentAccountMasked").value(
                        issued.studentAccount().substring(0, 2) + "****" + issued.studentAccount().substring(6)))
                .andExpect(jsonPath("$[0].bound").value(true));

        Long studentId = jdbcTemplate.queryForObject(
                "select id from edu_student where student_user_id=?", Long.class, issued.studentUserId());
        mockMvc.perform(post("/api/v1/students/{studentId}/wechat-unbindings", studentId)
                        .header("Authorization", "Bearer " + parentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"确认解绑学生微信\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_student_wechat_binding", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_student_wechat_binding_audit where event_type='UNBIND'",
                Integer.class)).isEqualTo(1);

        exchangeWechat()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingRequired").value(true));
    }

    @Test
    void refusesSelfBindingWhenStudentHasNoPrimaryParent() throws Exception {
        IssuedStudentCredential issued = createStudent("无主监护学生", null);
        String bindingTicket = objectMapper.readTree(exchangeWechat().andReturn()
                        .getResponse().getContentAsString())
                .path("bindingTicket").asText();

        mockMvc.perform(post("/api/v1/auth/student-wechat-binding-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bindingTicket":"%s",
                                  "studentAccount":"%s",
                                  "loginCode":"%s",
                                  "deviceId":"student-wechat-device",
                                  "deviceName":"学生微信小程序"
                                }
                                """.formatted(bindingTicket, issued.studentAccount(), issued.plainLoginCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_WECHAT_BINDING_UNAVAILABLE"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_student_wechat_binding", Integer.class)).isZero();
    }

    @Test
    void hidesCapabilityAndRejectsWechatEndpointsWhenFeatureIsDisabled() throws Exception {
        featureToggleMapper.updateGlobalStatus("STUDENT_WECHAT_AUTH", FeatureStatus.DISABLED);

        mockMvc.perform(get("/api/v1/public/capabilities?client=MINIAPP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentWechatAuthEnabled").value(false));
        exchangeWechat()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void exposesEnabledWechatManagementCapabilityToWeb() throws Exception {
        mockMvc.perform(get("/api/v1/public/capabilities?client=WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentWechatAuthEnabled").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions exchangeWechat() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/student-wechat-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "temporaryCode":"student-wechat-code",
                          "deviceId":"student-wechat-device",
                          "deviceName":"学生微信小程序"
                        }
                        """));
    }

    private IssuedStudentCredential createStudent(String name, Long parentUserId) {
        IssuedStudentCredential issued = provisioningService.issue(name);
        Long studentId = idGenerator.nextId();
        studentMapper.insert(Student.create(studentId, name, "G4", issued.studentUserId()));
        if (parentUserId != null) {
            relationshipMapper.insertPrimaryAt(idGenerator.nextId(), parentUserId, studentId,
                    java.time.LocalDateTime.now());
        }
        return issued;
    }

    private String registerParentAndCompleteOnboarding() throws Exception {
        mockMvc.perform(post("/api/v1/auth/parent-sms-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"%s","purpose":"REGISTER_OR_LOGIN","clientType":"WEB"}
                                """.formatted(MOBILE)))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/parent-sessions/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile":"%s",
                                  "code":"%s",
                                  "clientType":"WEB",
                                  "deviceId":"parent-wechat-management",
                                  "deviceName":"家长浏览器",
                                  "agreementAccepted":true,
                                  "agreementVersion":"1"
                                }
                                """.formatted(MOBILE, FixedTestParentSmsCodeGenerator.CODE)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = body.path("session").path("accessToken").asText();
        mockMvc.perform(post("/api/v1/parent-onboarding/completion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        return token;
    }
}
