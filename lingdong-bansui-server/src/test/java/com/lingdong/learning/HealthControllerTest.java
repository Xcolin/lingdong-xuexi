package com.lingdong.learning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    /** 真实依赖探测：测试环境 H2 可用（db=true），无真实 Redis（redis=false）→ 503 DEGRADED，且不泄露内部细节。 */
    @Test
    void reportsRealDependencyProbeWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.application").value("lingdong-bansui-server"))
                .andExpect(jsonPath("$.db").value(true))
                .andExpect(jsonPath("$.redis").value(false));
    }
}
