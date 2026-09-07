package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentWechatBindingMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentWechatAuthenticationServiceTest {
    private final ParentWechatIdentityGateway gateway = mock(ParentWechatIdentityGateway.class);
    private final ParentWechatBindingTicketService ticketService = mock(ParentWechatBindingTicketService.class);
    private final ParentWechatBindingMapper bindingMapper = mock(ParentWechatBindingMapper.class);
    private final ParentPhoneAuthenticationService phoneService = mock(ParentPhoneAuthenticationService.class);
    private final AuthenticationApplicationService sessionService = mock(AuthenticationApplicationService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final ParentWechatIntegrationAccess integrationAccess = mock(ParentWechatIntegrationAccess.class);
    private final IdGenerator idGenerator = () -> 1874244142494646701L;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-09T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private ParentWechatAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new ParentWechatAuthenticationService(
                gateway, ticketService, bindingMapper, phoneService, sessionService,
                featureAccessService, integrationAccess, idGenerator, clock);
    }

    @Test
    void returnsOneTimeBindingTicketWithoutCreatingAccountOrSessionForUnknownWechat() {
        ParentWechatIdentity identity = identity();
        when(gateway.exchange("temporary-code")).thenReturn(identity);
        when(bindingMapper.findActiveByIdentity("wx-app", "openid-1")).thenReturn(null);
        when(ticketService.issue(identity)).thenReturn("binding-ticket");

        ParentWechatSessionExchange result = service.exchange(sessionCommand());

        assertThat(result.bindingRequired()).isTrue();
        assertThat(result.bindingTicket()).isEqualTo("binding-ticket");
        assertThat(result.session()).isNull();
        verify(phoneService, never()).verifyWechatBindingMobile(any());
        verify(sessionService, never()).createSession(anyLong(), any(), any(), any());
    }

    @Test
    void createsMiniappSessionForExistingActiveBinding() {
        ParentWechatBinding binding = new ParentWechatBinding(
                51L, 21L, "wx-app", "openid-1", "unionid-1", ParentWechatBindingStatus.ACTIVE,
                LocalDateTime.now(clock), null);
        when(gateway.exchange("temporary-code")).thenReturn(identity());
        when(bindingMapper.findActiveByIdentity("wx-app", "openid-1")).thenReturn(binding);
        when(phoneService.getParentState(21L)).thenReturn(new ParentAuthState(false, false, "1"));
        when(sessionService.createSession(21L, AuthClientType.MINIAPP, "mini-device", "家长小程序"))
                .thenReturn(authenticatedSession());
        when(bindingMapper.touchLastLogin(eq(51L), any(LocalDateTime.class))).thenReturn(1);

        ParentWechatSessionExchange result = service.exchange(sessionCommand());

        assertThat(result.bindingRequired()).isFalse();
        assertThat(result.session().session()).isEqualTo(authenticatedSession());
        verify(sessionService).createSession(21L, AuthClientType.MINIAPP, "mini-device", "家长小程序");
        verify(bindingMapper).touchLastLogin(eq(51L), any(LocalDateTime.class));
    }

    @Test
    void bindsVerifiedMobileAndCreatesMiniappSessionAtomically() {
        when(ticketService.consume("binding-ticket")).thenReturn(identity().withoutSessionKey());
        when(phoneService.verifyWechatBindingMobile(any(ParentWechatMobileBindingCommand.class)))
                .thenReturn(new VerifiedParentAccount(21L, true, "1"));
        when(bindingMapper.findByUserId(21L)).thenReturn(null);
        when(bindingMapper.findActiveByIdentity("wx-app", "openid-1")).thenReturn(null);
        when(bindingMapper.insert(any(ParentWechatBinding.class))).thenReturn(1);
        when(sessionService.createSession(21L, AuthClientType.MINIAPP, "mini-device", "家长小程序"))
                .thenReturn(authenticatedSession());

        ParentPhoneAuthenticatedSession result = service.bind(bindingCommand());

        assertThat(result.onboardingRequired()).isTrue();
        assertThat(result.agreementAcceptanceRequired()).isFalse();
        verify(bindingMapper).insert(any(ParentWechatBinding.class));
        verify(sessionService).createSession(21L, AuthClientType.MINIAPP, "mini-device", "家长小程序");
    }

    @Test
    void refusesAutomaticOverwriteWhenParentOrWechatAlreadyBound() {
        when(ticketService.consume("binding-ticket")).thenReturn(identity().withoutSessionKey());
        when(phoneService.verifyWechatBindingMobile(any(ParentWechatMobileBindingCommand.class)))
                .thenReturn(new VerifiedParentAccount(21L, false, "1"));
        when(bindingMapper.findByUserId(21L)).thenReturn(new ParentWechatBinding(
                52L, 21L, "wx-app", "another-openid", null, ParentWechatBindingStatus.ACTIVE,
                LocalDateTime.now(clock), null));

        assertThatThrownBy(() -> service.bind(bindingCommand()))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(bindingMapper, never()).insert(any());
        verify(sessionService, never()).createSession(anyLong(), any(), any(), any());
    }

    private ParentWechatSessionCommand sessionCommand() {
        return new ParentWechatSessionCommand("temporary-code", "mini-device", "家长小程序");
    }

    private ParentWechatBindingCommand bindingCommand() {
        return new ParentWechatBindingCommand(
                "binding-ticket", "13800138000", "384291", "mini-device", "家长小程序",
                true, "1", "source-address-hash");
    }

    private ParentWechatIdentity identity() {
        return new ParentWechatIdentity("wx-app", "openid-1", "unionid-1", "session-key");
    }

    private AuthenticatedSession authenticatedSession() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new AuthenticatedSession(41L, "access-token", "refresh-token",
                now.plusMinutes(30), now.plusDays(7));
    }
}
