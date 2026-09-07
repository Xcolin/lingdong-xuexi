package com.lingdong.learning.student.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParentRelationshipSecurityTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void keepsManagementAuthenticatedButAllowsPublicResponsesToReachFeatureGuard() throws Exception {
        mockMvc.perform(get("/api/v1/students/8910000000000000001/parent-relationships"))
                .andExpect(status().isUnauthorized());

        String body = """
                {
                  "mobile":"13800138000",
                  "smsCode":"384291",
                  "clientType":"WEB",
                  "deviceId":"security-device",
                  "deviceName":"安全测试浏览器",
                  "agreementAccepted":true,
                  "agreementVersion":"1"
                }
                """;
        mockMvc.perform(post("/api/v1/parent-relationship-invitations/8910000000000000002/acceptance")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        mockMvc.perform(post("/api/v1/parent-relationship-invitations/8910000000000000002/rejection")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }
}
