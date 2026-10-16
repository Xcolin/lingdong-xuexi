package com.lingdong.learning.auth.application;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RegistrationVerificationServiceTest {
 @Test void normalizesEmailAndRejectsInvalidChannelAndAddress(){
  assertThat(RegistrationVerificationService.normalize("EMAIL"," User@Example.com ")).isEqualTo("user@example.com");
  assertThatThrownBy(()->RegistrationVerificationService.normalize("EMAIL","x@bad")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RegistrationVerificationService.normalize("SMS","123")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->RegistrationVerificationService.normalize("OTHER","abc")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void redisUnavailableNeverAllowsVerification(){
  var service=new RegistrationVerificationService(mock(StringRedisTemplate.class),mock(RegistrationDelivery.class));
  assertThatThrownBy(()->service.consume("EMAIL","a@example.com","123456","127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void wrongOrConsumedOtpFailsAndNeverDeletesThroughNonAtomicClientCalls(){
  var redis=mock(StringRedisTemplate.class);var service=new RegistrationVerificationService(redis,mock(RegistrationDelivery.class));
  when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenAnswer(invocation->{
   String script=((org.springframework.data.redis.core.script.RedisScript<?>)invocation.getArgument(0)).getScriptAsString();
   return script.contains("INCR")?1L:0L;
  });
  assertThatThrownBy(()->service.consume("EMAIL","a@example.com","000000","local")).hasMessageContaining("验证码错误");
  verify(redis,never()).delete(anyString());
 }
 @Test void rateLimitBlocksDelivery(){
  var redis=mock(StringRedisTemplate.class);var delivery=mock(RegistrationDelivery.class);var service=new RegistrationVerificationService(redis,delivery);
  when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenReturn(21L);
  assertThatThrownBy(()->service.send("EMAIL","a@example.com","local")).hasMessageContaining("频繁");verifyNoInteractions(delivery);
 }
 @Test void failedDeliveryInvalidatesOldProofAndDoesNotStoreNewCode(){
  var redis=mock(StringRedisTemplate.class);var delivery=mock(RegistrationDelivery.class);var values=mock(org.springframework.data.redis.core.ValueOperations.class);
  when(redis.opsForValue()).thenReturn(values);when(values.setIfAbsent(anyString(),anyString(),any(java.time.Duration.class))).thenReturn(true);
  when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenReturn(1L);
  doThrow(new IllegalStateException("发送失败")).when(delivery).send(anyString(),anyString(),anyString());
  var service=new RegistrationVerificationService(redis,delivery);
  assertThatThrownBy(()->service.send("EMAIL","a@example.com","local")).hasMessageContaining("发送失败");
  var order=inOrder(redis,delivery);order.verify(redis).delete(startsWith("registration:otp:"));order.verify(delivery).send(eq("EMAIL"),eq("a@example.com"),matches("[0-9]{6}"));
  verify(values,never()).set(anyString(),anyString(),any(java.time.Duration.class));
 }
 @Test void changedSendGenerationRejectsOldDeliveryPublishing(){
  var redis=mock(StringRedisTemplate.class);var delivery=mock(RegistrationDelivery.class);var values=mock(org.springframework.data.redis.core.ValueOperations.class);
  when(redis.opsForValue()).thenReturn(values);when(values.setIfAbsent(anyString(),anyString(),any(java.time.Duration.class))).thenReturn(true);
  when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class))).thenAnswer(invocation->{
   String script=((org.springframework.data.redis.core.script.RedisScript<?>)invocation.getArgument(0)).getScriptAsString();return script.contains("INCR")?1L:0L;
  });
  var service=new RegistrationVerificationService(redis,delivery);
  assertThatThrownBy(()->service.send("EMAIL","a@example.com","local")).hasMessageContaining("已失效");
  verify(delivery).send(eq("EMAIL"),eq("a@example.com"),matches("[0-9]{6}"));
  verify(values,never()).set(anyString(),anyString(),any(java.time.Duration.class));
 }
 @Test void digestDoesNotExposeCodeOrContact(){assertThat(RegistrationVerificationService.digest("a@example.com:123456")).hasSize(64).doesNotContain("123456","example");}
}
