package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.alert.ErrorAlertService;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ApiExceptionHandler;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import com.lingdong.learning.growthpoint.application.GrowthReviewSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 绑定及错误契约；测试身份解析不代表真实认证链路已验收。 */
class GrowthReviewSubscriptionControllerTest {
    private static final long STUDENT = 1874244142494661102L;
    private static final String PATH = "/api/v1/growth-review-subscriptions/students/" + STUDENT;
    private final GrowthReviewSubscriptionService service = mock(GrowthReviewSubscriptionService.class);
    private final AuthenticatedUser user = new AuthenticatedUser(1874244142494661101L,
            1874244142494661109L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new GrowthReviewSubscriptionController(service))
                .setControllerAdvice(new ApiExceptionHandler(new SecurityErrorResponseFactory(), org.mockito.Mockito.mock(ErrorAlertService.class)))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedUser.class;
                    }
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory factory) { return user; }
                }).build();
    }

    @Test void readsOnlyPublicPreferenceWithStringIdentifier() throws Exception {
        when(service.get(user, STUDENT)).thenReturn(new GrowthReviewSubscriptionService.View(Long.toString(STUDENT), false, 0));
        mvc.perform(get(PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(Long.toString(STUDENT)))
                .andExpect(jsonPath("$.studentId").isString())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.parentUserId").doesNotExist())
                .andExpect(jsonPath("$.openid").doesNotExist());
        verify(service).get(user, STUDENT);
    }

    @Test void enableAndCancelUseServerPrincipalAndExpectedVersion() throws Exception {
        for (boolean enabled : List.of(true, false)) {
            when(service.set(user, STUDENT, enabled, 2)).thenReturn(
                    new GrowthReviewSubscriptionService.View(Long.toString(STUDENT), enabled, 3));
            mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":" + enabled + ",\"version\":2,\"parentUserId\":\"forged\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(enabled))
                    .andExpect(jsonPath("$.version").value(3));
            verify(service).set(user, STUDENT, enabled, 2);
        }
        verifyNoMoreInteractions(service);
    }

    @Test void malformedBodiesNeverReachService() throws Exception {
        for (String body : List.of("{}", "{\"enabled\":true}", "{\"version\":0}",
                "{\"enabled\":null,\"version\":0}", "{\"enabled\":false,\"version\":-1}",
                "{\"enabled\":true,\"version\":9223372036854775808}", "{")) {
            mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test void malformedPathNeverReachesService() throws Exception {
        for (String id : List.of("not-an-id", "9223372036854775808")) {
            mvc.perform(get("/api/v1/growth-review-subscriptions/students/" + id))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void errorsKeepStableCodesAndDoNotExposeInternalDetails() throws Exception {
        var errors = List.of(new IllegalStateException("private-state"),
                new SystemOperationAccessDeniedException("private-permission"),
                new FeatureDisabledException("private-feature"));
        String[] codes = {"STATE_CONFLICT", "ACCESS_DENIED", "FEATURE_DISABLED"};
        int[] statuses = {409, 403, 409};
        for (int i = 0; i < errors.size(); i++) {
            doThrow(errors.get(i)).when(service).set(user, STUDENT, true, 1);
            mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"enabled\":true,\"version\":1}"))
                    .andExpect(status().is(statuses[i])).andExpect(jsonPath("$.code").value(codes[i]))
                    .andExpect(header().exists("X-Request-Id"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-"))));
        }
    }
}
