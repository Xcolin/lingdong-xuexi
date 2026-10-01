package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.domain.RoleType;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentPhoneAuthenticationServiceTest {
    private static final String MOBILE = "13800138000";
    private static final String CODE = "384291";
    private static final String AGREEMENT_VERSION = "1";
    private static final Long PARENT_ROLE_ID = 1874244142494646277L;

    private final ParentSmsVerificationService smsService = mock(ParentSmsVerificationService.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final ParentAuthenticationMapper parentMapper = mock(ParentAuthenticationMapper.class);
    private final AuthenticationApplicationService authenticationService = mock(AuthenticationApplicationService.class);
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AtomicLong ids = new AtomicLong(1874244142494646600L);
    private final IdGenerator idGenerator = ids::incrementAndGet;
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private ParentPhoneAuthenticationService service;

    @BeforeEach
    void setUp() {
        when(parentMapper.findCurrentAgreementVersion()).thenReturn(AGREEMENT_VERSION);
        when(roleMapper.findByCode("PARENT")).thenReturn(parentRole());
        when(userMapper.insert(any(User.class))).thenReturn(1);
        when(userRoleMapper.insert(anyLong(), anyLong(), anyLong(), eq(null), eq("GLOBAL"))).thenReturn(1);
        when(parentMapper.insertProfile(any(ParentProfile.class))).thenReturn(1);
        when(parentMapper.insertAgreementAcceptance(any(ParentAgreementAcceptance.class))).thenReturn(1);
        when(authenticationService.createSession(anyLong(), any(), any(), any())).thenReturn(session());
        service = new ParentPhoneAuthenticationService(
                smsService, userMapper, userRoleMapper, roleMapper, parentMapper,
                authenticationService, featureAccessService, idGenerator, clock, new ParentSmsProperties(),
                passwordPolicy, passwordEncoder);
    }

    @Test
    void registersNewFamilyUserWithParentRoleAgreementAndPendingOnboarding() {
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(null);

        ParentPhoneAuthenticatedSession result = service.loginBySms(command(true));

        assertThat(result.session()).isEqualTo(session());
        assertThat(result.onboardingRequired()).isTrue();
        assertThat(result.agreementAcceptanceRequired()).isFalse();
        assertThat(result.currentAgreementVersion()).isEqualTo(AGREEMENT_VERSION);
        verify(userMapper).insert(any(User.class));
        verify(userRoleMapper).insert(anyLong(), anyLong(), eq(PARENT_ROLE_ID), eq(null), eq("GLOBAL"));
        verify(parentMapper).insertProfile(any(ParentProfile.class));
        verify(parentMapper).insertAgreementAcceptance(any(ParentAgreementAcceptance.class));
        verify(smsService).verifyAndConsume(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, CODE);
    }

    @Test
    void definesWechatBindingAsAnIndependentSmsPurpose() {
        assertThat(ParentSmsPurpose.valueOf("WECHAT_BIND"))
                .isNotEqualTo(ParentSmsPurpose.REGISTER_OR_LOGIN)
                .isNotEqualTo(ParentSmsPurpose.RESET_PASSWORD);
    }

    @Test
    void definesIndependentRelationshipSmsPurposes() {
        assertThat(ParentSmsPurpose.valueOf("SECONDARY_PARENT_BIND"))
                .isNotEqualTo(ParentSmsPurpose.PRIMARY_PARENT_TRANSFER)
                .isNotEqualTo(ParentSmsPurpose.REGISTER_OR_LOGIN)
                .isNotEqualTo(ParentSmsPurpose.WECHAT_BIND);
    }

    @Test
    void rejectsAuthenticatedLifecyclePurposesFromPublicSmsIssuance() {
        for (ParentSmsPurpose purpose : new ParentSmsPurpose[]{
                ParentSmsPurpose.CHANGE_MOBILE_CURRENT,
                ParentSmsPurpose.CHANGE_MOBILE_NEW,
                ParentSmsPurpose.ACCOUNT_CANCELLATION,
                ParentSmsPurpose.MANUAL_MOBILE_RECOVERY_NEW
        }) {
            assertThatThrownBy(() -> service.issueSmsCode(
                    MOBILE, purpose, AuthClientType.MINIAPP, "source-address-hash"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("登录");
        }

        verify(smsService, never()).issue(any(), any(), any(), any());
    }

    @Test
    void verifiesExistingParentForRelationshipWithoutCreatingSession() {
        User parent = user(UserType.FAMILY, UserStatus.ENABLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(pendingProfile(parent.id()));
        when(parentMapper.hasAgreementAcceptance(parent.id(), AGREEMENT_VERSION)).thenReturn(true);

        VerifiedParentAccount result = service.verifyRelationshipInvitationMobile(
                relationshipCommand(ParentSmsPurpose.SECONDARY_PARENT_BIND, AuthClientType.WEB, false));

        assertThat(result.userId()).isEqualTo(parent.id());
        assertThat(result.onboardingRequired()).isTrue();
        verify(smsService).verifyAndConsume(
                MOBILE, ParentSmsPurpose.SECONDARY_PARENT_BIND, AuthClientType.WEB, CODE);
        verify(authenticationService, never()).createSession(anyLong(), any(), any(), any());
    }

    @Test
    void registersUnregisteredParentAfterRelationshipVerificationAndCurrentAgreementAcceptance() {
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(null);

        VerifiedParentAccount result = service.verifyRelationshipInvitationMobile(
                relationshipCommand(ParentSmsPurpose.PRIMARY_PARENT_TRANSFER, AuthClientType.MINIAPP, true));

        assertThat(result.userId()).isNotNull();
        verify(smsService).verifyAndConsume(
                MOBILE, ParentSmsPurpose.PRIMARY_PARENT_TRANSFER, AuthClientType.MINIAPP, CODE);
        verify(userMapper).insert(any(User.class));
        verify(userRoleMapper).insert(anyLong(), anyLong(), eq(PARENT_ROLE_ID), eq(null), eq("GLOBAL"));
        verify(parentMapper).insertProfile(any(ParentProfile.class));
        verify(parentMapper).insertAgreementAcceptance(any(ParentAgreementAcceptance.class));
        verify(authenticationService, never()).createSession(anyLong(), any(), any(), any());
    }

    @Test
    void rejectsRelationshipVerificationWithoutCurrentAgreementOrForNonParentAccount() {
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(null);
        assertThatThrownBy(() -> service.verifyRelationshipInvitationMobile(
                relationshipCommand(ParentSmsPurpose.SECONDARY_PARENT_BIND, AuthClientType.WEB, false)))
                .isInstanceOf(ParentAgreementAcceptanceRequiredException.class);

        User organizationUser = user(UserType.ORGANIZATION, UserStatus.ENABLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(organizationUser);
        assertThatThrownBy(() -> service.verifyRelationshipInvitationMobile(
                relationshipCommand(ParentSmsPurpose.PRIMARY_PARENT_TRANSFER, AuthClientType.MINIAPP, true)))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(authenticationService, never()).createSession(anyLong(), any(), any(), any());
    }

    @Test
    void logsInExistingEnabledParentWithoutDuplicatingIdentityData() {
        User parent = user(UserType.FAMILY, UserStatus.ENABLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(
                new ParentProfile(31L, parent.id(), ParentOnboardingStatus.COMPLETED,
                        LocalDateTime.now(clock), LocalDateTime.now(clock)));
        when(parentMapper.hasAgreementAcceptance(parent.id(), AGREEMENT_VERSION)).thenReturn(true);

        ParentPhoneAuthenticatedSession result = service.loginBySms(command(false));

        assertThat(result.onboardingRequired()).isFalse();
        verify(userMapper, never()).insert(any());
        verify(userRoleMapper, never()).insert(anyLong(), anyLong(), anyLong(), any(), any());
        verify(parentMapper, never()).insertProfile(any());
        verify(parentMapper, never()).insertAgreementAcceptance(any());
    }

    @Test
    void rejectsNonFamilyDisabledAndCancelledAccountsWithSameAuthenticationFailure() {
        User organizationUser = user(UserType.ORGANIZATION, UserStatus.ENABLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(organizationUser);
        assertThatThrownBy(() -> service.loginBySms(command(true)))
                .isInstanceOf(AuthenticationFailedException.class);

        User disabledParent = user(UserType.FAMILY, UserStatus.DISABLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(disabledParent);
        assertThatThrownBy(() -> service.loginBySms(command(true)))
                .isInstanceOf(AuthenticationFailedException.class);

        User cancelledParent = user(UserType.FAMILY, UserStatus.CANCELLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(cancelledParent);
        assertThatThrownBy(() -> service.loginBySms(command(true)))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(authenticationService, never()).createSession(anyLong(), any(), any(), any());
    }

    @Test
    void consumesVerifiedCodeButCreatesNoSessionWithoutCurrentAgreementAcceptance() {
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(null);

        assertThatThrownBy(() -> service.loginBySms(command(false)))
                .isInstanceOf(ParentAgreementAcceptanceRequiredException.class);

        verify(smsService).verifyAndConsume(
                MOBILE, ParentSmsPurpose.REGISTER_OR_LOGIN, AuthClientType.WEB, CODE);
        verify(userMapper, never()).insert(any());
        verify(authenticationService, never()).createSession(anyLong(), any(), any(), any());
    }

    @Test
    void acceptsCurrentAgreementOnceForExistingParent() {
        User parent = user(UserType.FAMILY, UserStatus.ENABLED);
        when(userMapper.findByIdForUpdate(parent.id())).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(pendingProfile(parent.id()));
        when(parentMapper.hasAgreementAcceptance(parent.id(), AGREEMENT_VERSION)).thenReturn(false, true);

        service.acceptCurrentAgreement(
                parent.id(), AuthClientType.WEB, AGREEMENT_VERSION, "source-address-hash");
        service.acceptCurrentAgreement(
                parent.id(), AuthClientType.WEB, AGREEMENT_VERSION, "source-address-hash");

        verify(parentMapper).insertAgreementAcceptance(any(ParentAgreementAcceptance.class));
    }

    @Test
    void completesPendingOnboardingIdempotently() {
        User parent = user(UserType.FAMILY, UserStatus.ENABLED);
        when(userMapper.findByIdForUpdate(parent.id())).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id()))
                .thenReturn(pendingProfile(parent.id()))
                .thenReturn(new ParentProfile(31L, parent.id(), ParentOnboardingStatus.COMPLETED,
                        LocalDateTime.now(clock), LocalDateTime.now(clock)));
        when(parentMapper.completeOnboarding(eq(parent.id()), any(LocalDateTime.class))).thenReturn(1);

        service.completeOnboarding(parent.id());
        service.completeOnboarding(parent.id());

        verify(parentMapper).completeOnboarding(eq(parent.id()), any(LocalDateTime.class));
    }

    @Test
    void storesOnlyEncodedPasswordForAuthenticatedParent() {
        User parent = user(UserType.FAMILY, UserStatus.ENABLED);
        when(userMapper.findByIdForUpdate(parent.id())).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(pendingProfile(parent.id()));
        when(passwordEncoder.encode("ParentPassword1")).thenReturn("encoded-parent-password");
        when(userMapper.updatePasswordHash(parent.id(), "encoded-parent-password")).thenReturn(1);

        service.setPassword(parent.id(), "ParentPassword1");

        verify(passwordPolicy).validate("ParentPassword1");
        verify(userMapper).updatePasswordHash(parent.id(), "encoded-parent-password");
        verify(userMapper, never()).updatePasswordHash(parent.id(), "ParentPassword1");
    }

    @Test
    void resetsParentPasswordAfterPurposeBoundSmsVerification() {
        User parent = user(UserType.FAMILY, UserStatus.ENABLED);
        when(userMapper.findByMobileForUpdate(MOBILE)).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(pendingProfile(parent.id()));
        when(passwordEncoder.encode("ResetPassword1")).thenReturn("encoded-reset-password");
        when(userMapper.updatePasswordHash(parent.id(), "encoded-reset-password")).thenReturn(1);

        service.resetPassword(new ParentPasswordResetCommand(
                MOBILE, CODE, "ResetPassword1", AuthClientType.WEB));

        verify(smsService).verifyAndConsume(
                MOBILE, ParentSmsPurpose.RESET_PASSWORD, AuthClientType.WEB, CODE);
        verify(userMapper).updatePasswordHash(parent.id(), "encoded-reset-password");
    }

    @Test
    void returnsNeutralAuthenticationFailureForUnknownAuthenticatedUser() {
        when(userMapper.findByIdForUpdate(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.setPassword(999L, "ParentPassword1"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void createsMiniappSessionForEnabledParentPasswordLogin() {
        User parent = new User(21L, MOBILE, "家长用户", MOBILE, "encoded-password",
                UserType.FAMILY, UserStatus.ENABLED, null, null);
        when(userMapper.findByUsernameForUpdate(MOBILE)).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(pendingProfile(parent.id()));
        when(parentMapper.hasAgreementAcceptance(parent.id(), AGREEMENT_VERSION)).thenReturn(false);
        when(passwordEncoder.matches("ParentPassword1", "encoded-password")).thenReturn(true);

        ParentPhoneAuthenticatedSession result = service.loginByPassword(new ParentPasswordLoginCommand(
                MOBILE, "ParentPassword1", AuthClientType.MINIAPP, "mini-device", "家长小程序"));

        assertThat(result.onboardingRequired()).isTrue();
        assertThat(result.agreementAcceptanceRequired()).isTrue();
        verify(authenticationService).createSession(parent.id(), AuthClientType.MINIAPP, "mini-device", "家长小程序");
    }

    @Test
    void rejectsUnknownAndIncorrectParentPasswordWithSameFailure() {
        when(userMapper.findByUsernameForUpdate(MOBILE)).thenReturn(null);
        assertThatThrownBy(() -> service.loginByPassword(new ParentPasswordLoginCommand(
                MOBILE, "ParentPassword1", AuthClientType.MINIAPP, "mini-device", "家长小程序")))
                .isInstanceOf(AuthenticationFailedException.class);

        User parent = new User(21L, MOBILE, "家长用户", MOBILE, "encoded-password",
                UserType.FAMILY, UserStatus.ENABLED, null, null);
        when(userMapper.findByUsernameForUpdate(MOBILE)).thenReturn(parent);
        when(userRoleMapper.hasRoleCode(parent.id(), "PARENT")).thenReturn(true);
        when(parentMapper.findProfileByUserId(parent.id())).thenReturn(pendingProfile(parent.id()));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);
        assertThatThrownBy(() -> service.loginByPassword(new ParentPasswordLoginCommand(
                MOBILE, "wrong-password", AuthClientType.MINIAPP, "mini-device", "家长小程序")))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void createsIndependentSessionForVerifiedRelationshipParent() {
        VerifiedParentAccount account = new VerifiedParentAccount(21L, true, AGREEMENT_VERSION);

        ParentPhoneAuthenticatedSession result = service.createRelationshipSession(
                account, AuthClientType.MINIAPP, "relationship-device", "家长关系小程序");

        assertThat(result.session()).isEqualTo(session());
        assertThat(result.onboardingRequired()).isTrue();
        verify(authenticationService).createSession(
                21L, AuthClientType.MINIAPP, "relationship-device", "家长关系小程序");
    }

    private ParentSmsLoginCommand command(boolean agreementAccepted) {
        return new ParentSmsLoginCommand(
                MOBILE, CODE, AuthClientType.WEB, "browser-device", "浏览器",
                agreementAccepted, AGREEMENT_VERSION, "source-address-hash");
    }

    private ParentRelationshipMobileVerificationCommand relationshipCommand(
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            boolean agreementAccepted
    ) {
        return new ParentRelationshipMobileVerificationCommand(
                MOBILE, CODE, purpose, clientType, agreementAccepted,
                AGREEMENT_VERSION, "source-address-hash");
    }

    private User user(UserType type, UserStatus status) {
        return new User(21L, MOBILE, "家长用户", MOBILE, null, type, status, null, null);
    }

    private ParentProfile pendingProfile(Long userId) {
        return new ParentProfile(31L, userId, ParentOnboardingStatus.PENDING,
                LocalDateTime.now(clock), null);
    }

    private Role parentRole() {
        return new Role(PARENT_ROLE_ID, "PARENT", "家长", RoleType.BUILT_IN,
                RoleDataScope.SELF, true, RoleStatus.ENABLED, null, null, null);
    }

    private AuthenticatedSession session() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new AuthenticatedSession(41L, "access-token", "refresh-token",
                now.plusMinutes(30), now.plusDays(7));
    }
}
