package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.AnonymousRankQueryService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 结构测试，真实对象授权由服务专项验证。 */
class AnonymousRankControllerTest {
    private static final long STUDENT=1874244142494692002L, CLASS=1874244142494692003L;
    private static final String PATH="/api/v1/anonymous-ranks/students/"+STUDENT+"/classes/"+CLASS;
    @Test void withdrawalOptionsEndpointExists() throws Exception {
        var service=mock(AnonymousRankQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnonymousRankController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/v1/anonymous-ranks/preferences")).andExpect(status().isOk());
    }
    @Test void studentOptionsEndpointIsAvailable() throws Exception {
        var service=mock(AnonymousRankQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnonymousRankController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/v1/anonymous-ranks/students")).andExpect(status().isOk());
    }
    @Test void classOptionsEndpointIsAvailable() throws Exception {
        var service=mock(AnonymousRankQueryService.class);
        when(service.classes(null,STUDENT)).thenReturn(List.of(new AnonymousRankMapper.ClassOption(String.valueOf(CLASS),"当前班级")));
        var mvc=MockMvcBuilders.standaloneSetup(new AnonymousRankController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/v1/anonymous-ranks/students/"+STUDENT+"/classes"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"classId\":\""+CLASS+"\",\"className\":\"当前班级\"}]",true));
        verify(service).classes(null,STUDENT);
    }
    @Test void rankingOnlyContainsRankAndPoints() throws Exception {
        var service=mock(AnonymousRankQueryService.class);
        when(service.ranking(null,STUDENT,CLASS)).thenReturn(List.of(new AnonymousRankMapper.Row(1L,20L)));
        var mvc=MockMvcBuilders.standaloneSetup(new AnonymousRankController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(content().json("[{\"rank\":1,\"points\":20}]",true));
        verify(service).ranking(null,STUDENT,CLASS);
    }
    @Test void readsAndUpdatesPreferenceWithVersion() throws Exception {
        var service=mock(AnonymousRankQueryService.class);
        when(service.preference(null,STUDENT,CLASS)).thenReturn(new AnonymousRankQueryService.Preference(false,0));
        when(service.set(null,STUDENT,CLASS,true,0)).thenReturn(new AnonymousRankQueryService.Preference(true,1));
        var mvc=MockMvcBuilders.standaloneSetup(new AnonymousRankController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get(PATH+"/preference")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(put(PATH+"/preference").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        verify(service).set(null,STUDENT,CLASS,true,0);
    }
    @Test void rejectsInvalidBodiesBeforeService() throws Exception {
        var service=mock(AnonymousRankQueryService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new AnonymousRankController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        for (String body:List.of("{}","{\"enabled\":true}","{\"enabled\":false,\"version\":-1}")) {
            mvc.perform(put(PATH+"/preference").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
