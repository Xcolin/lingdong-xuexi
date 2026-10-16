package com.lingdong.learning.auth.application;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Delivery configuration and failures never expose provider credentials. */
@Service
public class RegistrationDelivery {
    private final ObjectProvider<JavaMailSender> mail;
    private final boolean enabled;
    private final String from, smsUser, smsPassword, signature;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public RegistrationDelivery(ObjectProvider<JavaMailSender> mail,
        @Value("${registration.mail.enabled:false}") boolean enabled,
        @Value("${registration.mail.from:}") String from,
        @Value("${registration.smsbao.username:}") String smsUser,
        @Value("${registration.smsbao.password-md5:}") String smsPassword,
        @Value("${registration.smsbao.signature:重庆灵动递能}") String signature) {
        this(mail,enabled,from,smsUser,smsPassword,signature,HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build());
    }
    RegistrationDelivery(ObjectProvider<JavaMailSender> mail,boolean enabled,String from,String smsUser,String smsPassword,String signature,HttpClient http) {
        this.http=http;this.mail=mail;this.enabled=enabled;this.from=from;
        this.smsUser=smsUser;this.smsPassword=smsPassword;this.signature=signature;
    }
    public boolean emailEnabled(){return enabled&&!from.isBlank()&&mail.getIfAvailable()!=null;}
    public boolean smsEnabled(){return !smsUser.isBlank()&&smsPassword.matches("[A-Fa-f0-9]{32}")&&!signature.isBlank();}
    public void send(String channel,String destination,String code) {
        if("EMAIL".equals(channel)) {
            if(!emailEnabled())throw new IllegalStateException("邮箱验证服务未配置");
            var message=new SimpleMailMessage();message.setFrom(from);message.setTo(destination);
            message.setSubject("灵动伴随验证码");
            message.setText("您的灵动伴随验证码是："+code+"，5 分钟内有效。请勿将验证码提供给他人。如非本人操作，请忽略此邮件。");
            try{mail.getIfAvailable().send(message);}catch(RuntimeException e){throw new IllegalStateException("邮件发送失败，请稍后重试");}
        } else if("SMS".equals(channel)) {
            sendSms(destination,code,300);
        } else throw new IllegalArgumentException("验证方式无效");
    }
    public void sendSms(String destination,String code,long ttlSeconds) {
        if(!smsEnabled())throw new SmsDeliveryUnavailableException("短信验证服务未配置");
        if(ttlSeconds<=0)throw new IllegalArgumentException("验证码有效期无效");
        String content="【"+signature+"】您的验证码是"+code+","+ttlSeconds+"秒内有效";
        URI uri=URI.create("https://api.smsbao.com/sms?u="+encode(smsUser)+"&p="+encode(smsPassword)+"&m="+encode(destination)+"&c="+encode(content));
        try {
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()!=200)throw new SmsDeliveryUnavailableException("短信发送失败：供应商 HTTP "+response.statusCode());
            requireSmsSuccess(response.body());
        } catch(SmsDeliveryUnavailableException e){throw e;}
          catch(InterruptedException e){Thread.currentThread().interrupt();throw new SmsDeliveryUnavailableException("短信发送被中断，请稍后重试");}
          catch(java.net.http.HttpTimeoutException e){throw new SmsDeliveryUnavailableException("短信请求超时，请稍后重试");}
          catch(Exception e){throw new SmsDeliveryUnavailableException("短信服务连接失败，请稍后重试");}
    }
    static void requireSmsSuccess(String result){
        String code=result==null?"":result.trim();if("0".equals(code))return;
        String reason=switch(code){case "30"->"账号认证失败";case "40"->"账号不存在";case "41","60"->"余额不足或尚未充值";case "42"->"账号已过期";case "43"->"服务器 IP 未获允许";case "50"->"短信内容被供应商拒绝，请核查签名和模板";case "51"->"手机号格式错误";default->"供应商未受理，请稍后重试";};
        if(code.matches("[0-9]{1,3}"))org.slf4j.LoggerFactory.getLogger(RegistrationDelivery.class).warn("SMSBao submission refused, code={}",code);
        throw new SmsDeliveryUnavailableException("短信发送失败："+reason);
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
