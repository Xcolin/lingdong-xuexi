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
    private final com.lingdong.learning.user.infrastructure.persistence.UserMapper users=mock(com.lingdong.learning.user.infrastructure.persistence.UserMapper.class);
    private final com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper profiles=mock(com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper.class);
    private final ParentOnboardingAccessInterceptor interceptor =
            new ParentOnboardingAccessInterceptor(authenticationService, errorWriter,users,profiles);
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

    @Test void customWebFamilyCannotBypassCurrentAgreement() throws Exception {
        authenticateParent();
        var custom=new AuthenticatedUser(21L,31L,"custom","custom",AuthClientType.WEB,List.of("CUSTOM_FAMILY"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(custom,null,List.of()));
        when(request.getRequestURI()).thenReturn("/api/v1/rewards/students/123");
        when(authenticationService.getParentAccessState(21L)).thenReturn(new ParentAuthState(false,true,"2"));
        assertThat(interceptor.preHandle(request,response,new Object())).isFalse();
        verify(errorWriter).write(request,response,428,"PARENT_AGREEMENT_REQUIRED","请先同意当前用户协议");
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
    @Test void dualIdentityKeepsInstitutionBusinessAccessibleWhileFamilyGuidePending() throws Exception {
        authenticateParent();
        when(users.findById(21L)).thenReturn(com.lingdong.learning.user.domain.User.create(21L,"org","org",null,com.lingdong.learning.user.domain.UserType.ORGANIZATION));
        when(request.getRequestURI()).thenReturn("/api/v1/organization-workbench/context");
        assertThat(interceptor.preHandle(request,response,new Object())).isTrue();
        verify(authenticationService,never()).getParentAccessState(21L);
    }
    @Test void selectedFamilyRequiresItsGuideForDualIdentity() throws Exception {
        authenticateParent();
        when(users.findById(21L)).thenReturn(com.lingdong.learning.user.domain.User.create(21L,"org","org",null,com.lingdong.learning.user.domain.UserType.ORGANIZATION));
        when(request.getRequestURI()).thenReturn("/api/v1/learning-tasks");
        when(request.getAttribute(SelectedOrganizationContext.ATTRIBUTE)).thenReturn(com.lingdong.learning.organization.domain.Organization.create(9L,null,"FAMILY:9","F9","家庭","FAMILY","/F9/",0,null));
        when(authenticationService.getParentAccessState(21L)).thenReturn(new ParentAuthState(true,false,"1"));
        assertThat(interceptor.preHandle(request,response,new Object())).isFalse();
        verify(errorWriter).write(request,response,428,"PARENT_ONBOARDING_REQUIRED","请先完成家长首次引导");
    }

    private void authenticateParent() {
        when(users.findById(21L)).thenReturn(com.lingdong.learning.user.domain.User.create(21L,"family","family",null,com.lingdong.learning.user.domain.UserType.FAMILY));
        when(profiles.findProfileByUserId(21L)).thenReturn(mock(com.lingdong.learning.auth.application.ParentProfile.class));
        AuthenticatedUser parent = new AuthenticatedUser(
                21L, 31L, "13800138000", "家长用户", AuthClientType.MINIAPP, List.of("CUSTOM_FAMILY"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(parent, null, List.of()));
    }
}
