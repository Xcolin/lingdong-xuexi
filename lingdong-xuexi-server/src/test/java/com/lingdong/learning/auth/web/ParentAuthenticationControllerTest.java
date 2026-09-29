package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.infrastructure.memory.FixedTestParentSmsCodeGenerator;
import com.lingdong.learning.auth.infrastructure.memory.InMemoryParentSmsVerificationStore;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParentAuthenticationControllerTest {
    private static final String MOBILE = "13900139001";

    @Autowired private MockMvc mockMvc;
    @Autowired private FeatureToggleMapper featureToggleMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private InMemoryParentSmsVerificationStore smsVerificationStore;

    @BeforeEach
    void enableFeature() {
        smsVerificationStore.clear();
        featureToggleMapper.updateGlobalStatus("PARENT_PHONE_AUTH", FeatureStatus.ENABLED);
    }

    @Test
    void exposesContextIssuesCodeAndRegistersParentWithoutLeakingPlaintextCode() throws Exception {
        mockMvc.perform(get("/api/v1/public/parent-auth-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.wechatEnabled").value(false))
                .andExpect(jsonPath("$.agreementVersion").value("1"))
                .andExpect(jsonPath("$.codeExpiresInSeconds").value(300))
                .andExpect(jsonPath("$.retryAfterSeconds").value(60));

        MvcResult issueResult = mockMvc.perform(post("/api/v1/auth/parent-sms-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"%s","purpose":"REGISTER_OR_LOGIN","clientType":"WEB"}
                                """.formatted(MOBILE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.retryAfterSeconds").value(60))
                .andReturn();
        assertThat(issueResult.getResponse().getContentAsString())
                .doesNotContain(FixedTestParentSmsCodeGenerator.CODE);

        mockMvc.perform(post("/api/v1/auth/parent-sessions/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.sessionId").isString())
                .andExpect(jsonPath("$.session.accessToken").isString())
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(jsonPath("$.agreementAcceptanceRequired").value(false))
                .andExpect(jsonPath("$.currentAgreementVersion").value("1"));

        mockMvc.perform(post("/api/v1/auth/parent-sessions/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_SMS_VERIFICATION_FAILED"));
    }

    @Test
    void completesAgreementOnboardingAndPasswordLifecycle() throws Exception {
        String accessToken = registerAndLogin();

        mockMvc.perform(get("/api/v1/auth/parent-state")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(jsonPath("$.agreementAcceptanceRequired").value(false))
                .andExpect(jsonPath("$.currentAgreementVersion").value("1"));

        mockMvc.perform(get("/api/v1/learning-tasks?page=1&pageSize=20")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.code").value("PARENT_ONBOARDING_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/parent-passwords")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"ParentPassword1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/parent-sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile":"%s",
                                  "password":"ParentPassword1",
                                  "clientType":"MINIAPP",
                                  "deviceId":"parent-mini-device",
                                  "deviceName":"家长小程序"
                                }
                                """.formatted(MOBILE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.accessToken").isString())
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(jsonPath("$.agreementAcceptanceRequired").value(false));
        assertThat(jdbcTemplate.queryForObject(
                "select client_type from auth_device_session where device_id='parent-mini-device'",
                String.class)).isEqualTo("MINIAPP");

        mockMvc.perform(post("/api/v1/parent-onboarding/completion")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select onboarding_status from auth_parent_profile p join sys_user u on u.id=p.user_id where u.mobile=?",
                String.class, MOBILE)).isEqualTo("COMPLETED");

        jdbcTemplate.update(
                "update sys_config set config_value='2' where config_key='auth.parent-agreement.current-version'");
        mockMvc.perform(get("/api/v1/auth/parent-state")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreementAcceptanceRequired").value(true));
        mockMvc.perform(post("/api/v1/auth/parent-agreement-acceptances")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreementVersion\":\"2\",\"clientType\":\"WEB\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/parent-state")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingRequired").value(false))
                .andExpect(jsonPath("$.agreementAcceptanceRequired").value(false));
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from auth_user_agreement_acceptance a
                join sys_user u on u.id=a.user_id
                where u.mobile=? and a.agreement_version='2'
                """, Integer.class, MOBILE)).isEqualTo(1);

        issueCode("RESET_PASSWORD");
        mockMvc.perform(post("/api/v1/auth/parent-password-resets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"%s","code":"%s","newPassword":"ResetPassword1","clientType":"WEB"}
                                """.formatted(MOBILE, FixedTestParentSmsCodeGenerator.CODE)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"ResetPassword1","deviceId":"password-device","deviceName":"密码浏览器"}
                                """.formatted(MOBILE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void requiresVerifiedMobileOnFirstWechatAuthorizationAndThenLogsInDirectly() throws Exception {
        featureToggleMapper.updateGlobalStatus("PARENT_WECHAT_AUTH", FeatureStatus.ENABLED);
        mockMvc.perform(get("/api/v1/public/parent-auth-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wechatEnabled").value(true));

        MvcResult exchangeResult = mockMvc.perform(post("/api/v1/auth/parent-wechat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wechatSessionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingRequired").value(true))
                .andExpect(jsonPath("$.bindingTicket").isString())
                .andExpect(jsonPath("$.session").doesNotExist())
                .andReturn();
        String exchangeBody = exchangeResult.getResponse().getContentAsString();
        assertThat(exchangeBody).doesNotContain("openid-test").doesNotContain("session-key-test");
        String bindingTicket = objectMapper.readTree(exchangeBody).path("bindingTicket").asText();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys_user where mobile=?", Integer.class, MOBILE)).isZero();

        mockMvc.perform(post("/api/v1/auth/parent-sms-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"%s","purpose":"WECHAT_BIND","clientType":"MINIAPP"}
                                """.formatted(MOBILE)))
                .andExpect(status().isCreated());
        String bindingBody = """
                {
                  "bindingTicket":"%s",
                  "mobile":"%s",
                  "smsCode":"%s",
                  "deviceId":"wechat-mini-device",
                  "deviceName":"家长微信小程序",
                  "agreementAccepted":true,
                  "agreementVersion":"1"
                }
                """.formatted(bindingTicket, MOBILE, FixedTestParentSmsCodeGenerator.CODE);
        mockMvc.perform(post("/api/v1/auth/parent-wechat-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindingBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.accessToken").isString())
                .andExpect(jsonPath("$.onboardingRequired").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_parent_wechat_binding", Integer.class)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/auth/parent-wechat-bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindingBody))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("PARENT_WECHAT_BINDING_TICKET_INVALID"));

        mockMvc.perform(post("/api/v1/auth/parent-wechat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wechatSessionBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingRequired").value(false))
                .andExpect(jsonPath("$.bindingTicket").doesNotExist())
                .andExpect(jsonPath("$.session.session.accessToken").isString());

        jdbcTemplate.update("update sys_user set status='DISABLED' where mobile=?", MOBILE);
        mockMvc.perform(post("/api/v1/auth/parent-wechat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wechatSessionBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void rejectsWechatEndpointsWhileFeatureIsDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/auth/parent-wechat-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wechatSessionBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_parent_wechat_binding", Integer.class)).isZero();
    }

    @Test
    void changesParentMobileRevokesSessionsAndManagesCancellationCoolingOff() throws Exception {
        String accessToken = registerAndLogin();
        mockMvc.perform(post("/api/v1/auth/parent-passwords")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"ResetPassword1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/parent-account-lifecycle")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedMobile").value("139****9001"))
                .andExpect(jsonPath("$.activeStudentRelationshipCount").value(0))
                .andExpect(jsonPath("$.cancellationStatus").value("NONE"));

        mockMvc.perform(post("/api/v1/auth/parent-mobile-change/current-codes")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated());
        MvcResult ticketResult = mockMvc.perform(post("/api/v1/auth/parent-mobile-change-tickets")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(FixedTestParentSmsCodeGenerator.CODE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket").isString())
                .andReturn();
        String ticket = objectMapper.readTree(ticketResult.getResponse().getContentAsString())
                .path("ticket").asText();
        String newMobile = "13700137001";

        mockMvc.perform(post("/api/v1/auth/parent-mobile-change/new-codes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticket":"%s","newMobile":"%s"}
                                """.formatted(ticket, newMobile)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/parent-mobile-changes")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticket":"%s","newMobile":"%s","code":"%s"}
                                """.formatted(ticket, newMobile, FixedTestParentSmsCodeGenerator.CODE)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/parent-account-lifecycle")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys_user where mobile=? and username=?", Integer.class, newMobile, newMobile))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from auth_parent_mobile_change where old_mobile_digest<>? and new_mobile_digest<>?",
                Integer.class, MOBILE, newMobile)).isEqualTo(1);

        String cancellationToken = loginByParentPassword(newMobile, "parent-lifecycle-device");
        mockMvc.perform(post("/api/v1/auth/parent-account-cancellation-codes")
                        .header("Authorization", "Bearer " + cancellationToken))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/parent-account-cancellations")
                        .header("Authorization", "Bearer " + cancellationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","confirmation":"确认注销"}
                                """.formatted(FixedTestParentSmsCodeGenerator.CODE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cancellationId").isString())
                .andExpect(jsonPath("$.status").value("COOLING_OFF"))
                .andExpect(jsonPath("$.coolingEndsAt").isNotEmpty());
        mockMvc.perform(delete("/api/v1/auth/parent-account-cancellations/current")
                        .header("Authorization", "Bearer " + cancellationToken))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select status from auth_parent_account_cancellation", String.class)).isEqualTo("REVOKED");
    }

    @Test
    void rejectsParentAccountLifecycleAfterDynamicPermissionRemoval() throws Exception {
        String accessToken = registerAndLogin();
        jdbcTemplate.update("""
                delete from sys_role_permission
                where permission_id = (
                    select id from sys_permission where permission_code='PARENT_ACCOUNT_LIFECYCLE_MANAGE'
                )
                """);

        mockMvc.perform(get("/api/v1/auth/parent-account-lifecycle")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void hidesCapabilityAndRejectsDirectLifecycleAccessWhileFeatureIsDisabled() throws Exception {
        String accessToken = registerAndLogin();
        featureToggleMapper.updateGlobalStatus("PARENT_ACCOUNT_LIFECYCLE", FeatureStatus.DISABLED);

        mockMvc.perform(get("/api/v1/public/capabilities?client=WEB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentAccountLifecycleEnabled").value(false));
        mockMvc.perform(get("/api/v1/auth/parent-account-lifecycle")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    private String registerAndLogin() throws Exception {
        issueCode("REGISTER_OR_LOGIN");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/parent-sessions/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("session").path("accessToken").asText();
    }

    private String loginByParentPassword(String mobile, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/parent-sessions/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile":"%s",
                                  "password":"ResetPassword1",
                                  "clientType":"WEB",
                                  "deviceId":"%s",
                                  "deviceName":"家长生命周期浏览器"
                                }
                                """.formatted(mobile, deviceId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("session").path("accessToken").asText();
    }

    private void issueCode(String purpose) throws Exception {
        mockMvc.perform(post("/api/v1/auth/parent-sms-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mobile":"%s","purpose":"%s","clientType":"WEB"}
                                """.formatted(MOBILE, purpose)))
                .andExpect(status().isCreated());
    }

    private String loginBody() {
        return """
                {
                  "mobile":"%s",
                  "code":"%s",
                  "clientType":"WEB",
                  "deviceId":"parent-web-device",
                  "deviceName":"家长浏览器",
                  "agreementAccepted":true,
                  "agreementVersion":"1"
                }
                """.formatted(MOBILE, FixedTestParentSmsCodeGenerator.CODE);
    }

    private String wechatSessionBody() {
        return """
                {
                  "temporaryCode":"wechat-temporary-code",
                  "deviceId":"wechat-mini-device",
                  "deviceName":"家长微信小程序"
                }
                """;
    }

}
