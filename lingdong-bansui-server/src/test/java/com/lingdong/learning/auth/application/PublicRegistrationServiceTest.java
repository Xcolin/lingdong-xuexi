package com.lingdong.learning.auth.application;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.auth.domain.AuthClientType;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class PublicRegistrationServiceTest {
 private final UserMapper users=mock(UserMapper.class);private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final AuthenticationApplicationService auth=mock(AuthenticationApplicationService.class);private final RegistrationVerificationService codes=mock(RegistrationVerificationService.class);
 private PublicRegistrationService service(){var s=new PublicRegistrationService(users,jdbc,mock(IdGenerator.class),auth,codes);ReflectionTestUtils.setField(s,"version","v1");return s;}
 private PublicRegistrationService.Command command(boolean accepted,String confirmation){return new PublicRegistrationService.Command("EMAIL","new@example.com","123456","new_user","新账号","Password123",confirmation,accepted,"v1",AuthClientType.WEB,"device","浏览器");}
 @Test void agreementAndPasswordMismatchNeverConsumeOtp(){
  assertThatThrownBy(()->service().register(command(false,"Password123"),"local")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->service().register(command(true,"Different1"),"local")).isInstanceOf(IllegalArgumentException.class);
  verifyNoInteractions(codes,users,jdbc);
 }
 @Test void duplicateDestinationRequiresProofAndNeverOverwritesPassword(){
  when(auth.encodeManagedUserPassword("Password123")).thenReturn("hash");
  when(jdbc.queryForObject("select count(*) from auth_verified_email where email = ?",Long.class,"new@example.com")).thenReturn(1L);
  assertThatThrownBy(()->service().register(command(true,"Password123"),"local")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已有账号登录");
  verify(codes).consume("EMAIL","new@example.com","123456","local");verify(users,never()).insert(any());verify(users,never()).updatePasswordHash(any(),any());
 }
 @Test void successfulRegistrationCreatesPersonalAccountAndVerifiedBindingWithoutRoles(){
  when(auth.encodeManagedUserPassword("Password123")).thenReturn("hash");
  when(jdbc.queryForObject("select count(*) from auth_verified_email where email = ?",Long.class,"new@example.com")).thenReturn(0L);
  when(users.insert(any())).thenReturn(1);when(users.updatePasswordHash(any(),any())).thenReturn(1);
  service().register(command(true,"Password123"),"local");
  var captor=org.mockito.ArgumentCaptor.forClass(com.lingdong.learning.user.domain.User.class);verify(users).insert(captor.capture());
  assertThat(captor.getValue().type()).isEqualTo(com.lingdong.learning.user.domain.UserType.PERSONAL);
  assertThat(captor.getValue().mobile()).isNull();
  verify(auth).createSession(any(),eq(AuthClientType.WEB),eq("device"),eq("浏览器"));
 }
 @Test void knownUsernameConflictDoesNotConsumeValidOtp(){
  when(users.existsByUsername("new_user")).thenReturn(true);
  assertThatThrownBy(()->service().register(command(true,"Password123"),"local")).hasMessageContaining("已有账号登录");
  verifyNoInteractions(codes);verify(users,never()).insert(any());
 }
}
