package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountEmailAuthenticationControllerTest {
 final RegistrationVerificationService verification=mock(RegistrationVerificationService.class);
 final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 final AuthenticationApplicationService auth=mock(AuthenticationApplicationService.class);
 final HttpServletRequest request=mock(HttpServletRequest.class);
 final AccountEmailAuthenticationController controller=new AccountEmailAuthenticationController(verification,jdbc,auth);
 @Test void loginUsesSeparatePurposeAndExistingVerifiedAccount(){
  when(request.getRemoteAddr()).thenReturn("127.0.0.1");
  when(jdbc.queryForList(anyString(),eq(Long.class),eq("parent@example.com"))).thenReturn(List.of(7L));
  controller.login(new AccountEmailAuthenticationController.Login("parent@example.com","123456",AuthClientType.MINIAPP,"device","mobile"),request);
  verify(verification).consumeForPurpose("EMAIL","parent@example.com","123456","127.0.0.1","ACCOUNT_LOGIN:MINIAPP");
  verify(auth).loginExistingVerifiedAccount(7L,AuthClientType.MINIAPP,"device","mobile");
 }
 @Test void unboundAddressDoesNotCreateOrLoginAccount(){
  when(jdbc.queryForList(anyString(),eq(Long.class),eq("new@example.com"))).thenReturn(List.of());
  assertThrows(AuthenticationFailedException.class,()->controller.login(new AccountEmailAuthenticationController.Login("new@example.com","123456",AuthClientType.MINIAPP,"device","mobile"),request));
  verifyNoInteractions(auth);
 }
 @Test void wrongProofDoesNotResolveOrLoginAccount(){
  doThrow(new IllegalArgumentException("验证码无效")).when(verification).consumeForPurpose(anyString(),anyString(),anyString(),isNull(),eq("ACCOUNT_LOGIN:MINIAPP"));
  assertThrows(IllegalArgumentException.class,()->controller.login(new AccountEmailAuthenticationController.Login("parent@example.com","123456",AuthClientType.MINIAPP,"device","mobile"),request));
  verifyNoInteractions(jdbc,auth);
 }
}
