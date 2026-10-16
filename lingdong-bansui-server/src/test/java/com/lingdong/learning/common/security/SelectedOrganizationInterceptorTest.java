package com.lingdong.learning.common.security;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.datascope.application.OrganizationDataScope;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SelectedOrganizationInterceptorTest {
 final OrganizationMapper organizations=mock(OrganizationMapper.class);
 final UserOrganizationMapper memberships=mock(UserOrganizationMapper.class);
 final OrganizationDataScopeService scopes=mock(OrganizationDataScopeService.class);
 final SecurityErrorResponseWriter errors=mock(SecurityErrorResponseWriter.class);
 final SelectedOrganizationInterceptor interceptor=new SelectedOrganizationInterceptor(organizations,memberships,scopes,errors);
 final MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/classes");
 final MockHttpServletResponse response=new MockHttpServletResponse();
 @BeforeEach void setup(){var actor=new AuthenticatedUser(11L,22L,"parent","parent",AuthClientType.MINIAPP,List.of("PARENT_ADMIN"));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
 @Test void noHeaderLeavesExistingScopeUnchanged() throws Exception {assertThat(interceptor.preHandle(request,response,new Object())).isTrue();assertThat(SelectedOrganizationContext.organizationId()).isNull();verifyNoInteractions(organizations,memberships,scopes);}
 @Test void rejectsUnknownAndMalformedHeader() throws Exception {request.addHeader("X-Organization-Id","999");assertThat(interceptor.preHandle(request,response,new Object())).isFalse();request.removeHeader("X-Organization-Id");request.addHeader("X-Organization-Id","1,2");assertThat(interceptor.preHandle(request,response,new Object())).isFalse();}
 @Test void foreignOrganizationIsRejected() throws Exception {Organization organization=operational();when(organizations.findById(33L)).thenReturn(organization);when(scopes.resolve(11L)).thenReturn(OrganizationDataScope.empty());request.addHeader("X-Organization-Id","33");assertThat(interceptor.preHandle(request,response,new Object())).isFalse();assertThat(SelectedOrganizationContext.organizationId()).isNull();}
 @Test void actualMemberGetsOnlyValidatedRequestContext() throws Exception {var organization=operational();when(organizations.findById(33L)).thenReturn(organization);when(memberships.exists(11L,33L)).thenReturn(true);request.addHeader("X-Organization-Id","33");assertThat(interceptor.preHandle(request,response,new Object())).isTrue();assertThat(SelectedOrganizationContext.organizationId()).isEqualTo(33L);}
 @Test void onboardingDoesNotInheritPriorChoice() throws Exception {request.setRequestURI("/api/v1/organization-onboarding/my-organizations");request.addHeader("X-Organization-Id","999");assertThat(interceptor.preHandle(request,response,new Object())).isTrue();assertThat(SelectedOrganizationContext.organizationId()).isNull();verifyNoInteractions(organizations);}
 Organization operational(){Organization organization=mock(Organization.class);when(organization.id()).thenReturn(33L);when(organization.status()).thenReturn(OrganizationStatus.ENABLED);when(organization.effectiveStatus()).thenReturn(OrganizationEffectiveStatus.ENABLED);return organization;}
}

