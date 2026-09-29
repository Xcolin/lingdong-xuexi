package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.ParentAuthState;
import com.lingdong.learning.auth.application.ParentPhoneAuthenticationService;
import com.lingdong.learning.auth.domain.AuthClientType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentOnboardingAccessInterceptorTest {
    private final ParentPhoneAuthenticationService authenticationService =
            mock(ParentPhoneAuthenticationService.class);
    private final SecurityErrorResponseWriter errorWriter = mock(SecurityErrorResponseWriter.class);
    private final ParentOnboardingAccessInterceptor interceptor =
            new ParentOnboardingAccessInterceptor(authenticationService, errorWriter);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksOrdinaryApiUntilParentCompletesOnboarding() throws Exception {
        authenticateParent();
        when(request.getRequestURI()).thenReturn("/api/v1/learning-tasks");
        when(authenticationService.getParentAccessState(21L))
                .thenReturn(new ParentAuthState(true, false, "1"));

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        verify(errorWriter).write(request, response, 428,
                "PARENT_ONBOARDING_REQUIRED", "请先完成家长首次引导");
    }

    @Test
    void permitsAgreementAndOnboardingApisBeforeCompletion() throws Exception {
        authenticateParent();
        when(request.getRequestURI()).thenReturn("/api/v1/auth/parent-agreement-acceptances");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(authenticationService, never()).getParentAccessState(21L);
    }

    @Test
    void permitsLegacyParentWithoutPhoneAuthenticationProfile() throws Exception {
        authenticateParent();
        when(request.getRequestURI()).thenReturn("/api/v1/students");
        when(authenticationService.getParentAccessState(21L)).thenReturn(null);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        verify(errorWriter, never()).write(request, response, 428,
                "PARENT_ONBOARDING_REQUIRED", "请先完成家长首次引导");
    }

    private void authenticateParent() {
        AuthenticatedUser parent = new AuthenticatedUser(
                21L, 31L, "13800138000", "家长用户", AuthClientType.MINIAPP, List.of("PARENT"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(parent, null, List.of()));
    }
}
