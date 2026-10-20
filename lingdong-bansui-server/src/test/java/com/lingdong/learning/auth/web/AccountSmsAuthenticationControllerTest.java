package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.infrastructure.memory.*;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.common.id.IdGenerator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class AccountSmsAuthenticationControllerTest {
    static final String PHONE="13900008781";
    @Autowired MockMvc mvc;
    @Autowired UserMapper users;
    @Autowired IdGenerator ids;
    @Autowired InMemoryParentSmsVerificationStore proofs;
    @Autowired FeatureToggleMapper features;
    @Autowired com.lingdong.learning.organization.onboarding.infrastructure.persistence.MobileVerificationMapper mobileVerifications;
    @BeforeEach void setup(){proofs.clear();features.updateGlobalStatus("PARENT_PHONE_AUTH",FeatureStatus.ENABLED);}
    void issue(String client)throws Exception{mvc.perform(post("/api/v1/auth/login-sms-codes").contentType(MediaType.APPLICATION_JSON).content("{\"mobile\":\""+PHONE+"\",\"clientType\":\""+client+"\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.retryAfterSeconds").value(60)).andExpect(jsonPath("$.code").doesNotExist());}
    String body(String client){return "{\"mobile\":\""+PHONE+"\",\"code\":\""+FixedTestParentSmsCodeGenerator.CODE+"\",\"clientType\":\""+client+"\",\"deviceId\":\"sms-test\",\"deviceName\":\"短信测试\"}";}
    @Test void registeredAccountCanUseBothClientsAndProofCannotBeReplayed()throws Exception {
        var user=User.create(ids.nextId(),"sms_http_personal","短信账号",PHONE,UserType.PERSONAL);
        users.insert(user);
        // 已验证手机机制后，注册流程会写入 auth_verified_mobile；SMS 登录前需存在该记录，否则直接 401
        mobileVerifications.save(user.id(),PHONE);
        for(String client:new String[]{"WEB","MINIAPP"}){
            issue(client);
            mvc.perform(post("/api/v1/auth/sessions/sms").contentType(MediaType.APPLICATION_JSON).content(body(client)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.accessToken").isString()).andExpect(jsonPath("$.onboardingRequired").value(false));
            mvc.perform(post("/api/v1/auth/sessions/sms").contentType(MediaType.APPLICATION_JSON).content(body(client))).andExpect(status().isUnauthorized());
        }
    }
    @Test void unknownVerifiedPhoneCannotCreateAccount()throws Exception {
        issue("WEB");
        mvc.perform(post("/api/v1/auth/sessions/sms").contentType(MediaType.APPLICATION_JSON).content(body("WEB"))).andExpect(status().isBadRequest());
        assertThat(users.existsByMobile(PHONE)).isFalse();
    }
    @Test void legacyRegistrationPurposeCannotAuthenticateGenericSmsLogin()throws Exception {
        users.insert(User.create(ids.nextId(),"sms_http_purpose","用途隔离",PHONE,UserType.PERSONAL));
        mvc.perform(post("/api/v1/auth/parent-sms-codes").contentType(MediaType.APPLICATION_JSON).content("{\"mobile\":\""+PHONE+"\",\"purpose\":\"REGISTER_OR_LOGIN\",\"clientType\":\"WEB\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/sessions/sms").contentType(MediaType.APPLICATION_JSON).content(body("WEB"))).andExpect(status().isUnauthorized());
    }
}
