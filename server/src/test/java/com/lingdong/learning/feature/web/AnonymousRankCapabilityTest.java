package com.lingdong.learning.feature.web;

import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnonymousRankCapabilityTest {
    @Test void rankingIsHiddenByDefaultAndExposedToBothClientsWhenEnabled() throws Exception {
        var features = mock(FeatureAccessService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new PublicCapabilityController(features)).build();
        mvc.perform(get("/api/v1/public/capabilities?client=WEB"))
                .andExpect(jsonPath("$.anonymousClassRankEnabled").value(false));
        when(features.isEnabled("ANONYMOUS_CLASS_RANK",null)).thenReturn(true);
        mvc.perform(get("/api/v1/public/capabilities?client=WEB"))
                .andExpect(jsonPath("$.anonymousClassRankEnabled").value(true));
        mvc.perform(get("/api/v1/public/capabilities?client=MINIAPP"))
                .andExpect(jsonPath("$.anonymousClassRankEnabled").value(true));
    }
}
