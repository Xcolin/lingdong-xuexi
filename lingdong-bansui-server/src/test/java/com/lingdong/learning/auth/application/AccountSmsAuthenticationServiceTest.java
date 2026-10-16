package com.lingdong.learning.auth.application;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.domain.*;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AccountSmsAuthenticationServiceTest {
    final ParentSmsVerificationService codes=mock(ParentSmsVerificationService.class);
    final AuthenticationApplicationService auth=mock(AuthenticationApplicationService.class);
    final UserMapper users=mock(UserMapper.class);
    final ParentPhoneAuthenticationService parents=mock(ParentPhoneAuthenticationService.class);
    final FeatureAccessService features=mock(FeatureAccessService.class);
    AccountSmsAuthenticationService service(){return new AccountSmsAuthenticationService(codes,auth,users,parents,features);}
    AccountSmsAuthenticationService.Command command(){return new AccountSmsAuthenticationService.Command("13800138000","234567",AuthClientType.WEB,"device","设备");}
    @Test void existingPersonalLoginConsumesOnlyLoginProofAndNeverCreatesAccount() {
        when(users.findByMobileForUpdate("13800138000")).thenReturn(User.create(10L,"user","用户","13800138000",UserType.PERSONAL));
        var session=new AuthenticatedSession(20L,"access","refresh",LocalDateTime.now().plusMinutes(15),LocalDateTime.now().plusDays(1));
        when(auth.loginExistingAccountByMobile("13800138000",AuthClientType.WEB,"device","设备")).thenReturn(session);
        var result=service().login(command());
        assertThat(result.session()).isSameAs(session);assertThat(result.onboardingRequired()).isFalse();
        verify(codes).verifyAndConsume("13800138000",ParentSmsPurpose.ACCOUNT_LOGIN,AuthClientType.WEB,"234567");
        verify(users,never()).insert(any());
    }
    @Test void invalidOrUsedCodeCannotReachAccountLogin() {
        doThrow(new ParentSmsVerificationFailedException()).when(codes).verifyAndConsume(anyString(),any(),any(),anyString());
        assertThatThrownBy(()->service().login(command())).isInstanceOf(ParentSmsVerificationFailedException.class);
        verifyNoInteractions(auth,users);
    }
    @Test void verifiedUnknownMobileRequiresExplicitRegistration() {
        assertThatThrownBy(()->service().login(command())).hasMessageContaining("注册");
        verify(users,never()).insert(any());verifyNoInteractions(auth);
    }
    @Test void familyLoginReturnsRealGuideAndAgreementFlagsWithoutAcceptingAgreement() {
        when(users.findByMobileForUpdate("13800138000")).thenReturn(User.create(10L,"parent","家长","13800138000",UserType.FAMILY));
        when(parents.getParentAccessState(10L)).thenReturn(new ParentAuthState(true,true,"v1"));
        var result=service().login(command());
        assertThat(result.onboardingRequired()).isTrue();assertThat(result.agreementAcceptanceRequired()).isTrue();
        verify(parents,never()).acceptCurrentAgreement(any(),any(),any(),any());
    }
}
