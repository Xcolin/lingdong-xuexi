package com.lingdong.learning.auth.application;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.time.Duration;
@Service
public class RegistrationVerificationService {
 private final StringRedisTemplate redis;private final RegistrationDelivery delivery;private final SecureRandom random=new SecureRandom();
 public RegistrationVerificationService(StringRedisTemplate redis,RegistrationDelivery delivery){this.redis=redis;this.delivery=delivery;}
 public static String normalize(String channel,String destination){
  if(destination==null)throw new IllegalArgumentException("验证地址不能为空");String value=destination.trim().toLowerCase(Locale.ROOT);
  if("EMAIL".equals(channel)){if(value.length()>254||!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("邮箱格式不正确");}
  else if("SMS".equals(channel)){if(!value.matches("1[3-9][0-9]{9}"))throw new IllegalArgumentException("手机号格式不正确");}
  else throw new IllegalArgumentException("验证方式无效");return value;
 }
 public void send(String channel,String destination,String source){sendForPurpose(channel,destination,source,"REGISTRATION");}
 public void sendForPurpose(String channel,String destination,String source,String purpose){
  String address=digest(purpose+":"+channel+":"+destination);limit("source-send:"+digest(source),20,3600);limit("address-send:"+address,8,3600);
  String generation=UUID.randomUUID().toString();
  if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("registration:cooldown:"+address,generation,Duration.ofSeconds(60))))throw new IllegalArgumentException("请等待60秒后重试");
  redis.delete("registration:otp:"+address);
  String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));delivery.send(channel,destination,code);
  Long published=redis.execute(new DefaultRedisScript<>(
          "if redis.call('GET',KEYS[1])==ARGV[1] then redis.call('SET',KEYS[2],ARGV[2],'EX',300); return 1; end; return 0",Long.class),
          List.of("registration:cooldown:"+address,"registration:otp:"+address),generation,digest(address+":"+code));
  if(!Long.valueOf(1).equals(published))throw new IllegalArgumentException("验证码发送已失效，请重新获取最新验证码");
 }
 public void consume(String channel,String destination,String code,String source){consumeForPurpose(channel,destination,code,source,"REGISTRATION");}
 public void consumeForPurpose(String channel,String destination,String code,String source,String purpose){
  limit("source-verify:"+digest(source),60,3600);String address=digest(purpose+":"+channel+":"+destination);limit("address-verify:"+address,5,300);
  String script="local v=redis.call('GET',KEYS[1]); if v and v==ARGV[1] then redis.call('DEL',KEYS[1]); return 1; end; return 0";
  Long result=redis.execute(new DefaultRedisScript<>(script,Long.class),List.of("registration:otp:"+address),digest(address+":"+Objects.toString(code,"")));
  if(!Long.valueOf(1).equals(result))throw new IllegalArgumentException("验证码错误或已失效");
 }
 public void verifyForPurpose(String channel,String destination,String code,String source,String purpose){
  limit("source-verify:"+digest(source),60,3600);String address=digest(purpose+":"+channel+":"+destination);limit("address-verify:"+address,5,300);
  Long result=redis.execute(new DefaultRedisScript<>("local v=redis.call('GET',KEYS[1]); if v and v==ARGV[1] then return 1; end; return 0",Long.class),List.of("registration:otp:"+address),digest(address+":"+Objects.toString(code,"")));
  if(!Long.valueOf(1).equals(result))throw new IllegalArgumentException("验证码错误或已失效");
 }
 private void limit(String key,int max,int seconds){Long count=redis.execute(new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n",Long.class),List.of("registration:"+key),String.valueOf(seconds));if(count==null||count>max)throw new IllegalArgumentException("操作过于频繁，请稍后重试");}
 public static String digest(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException();}}
}


