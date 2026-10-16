package com.lingdong.learning.auth.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class RegistrationDeliveryTest {
    @Test void smsRequestUsesProvidedEndpointAndDynamicallyEncodedContent() throws Exception {
        var http=mock(java.net.http.HttpClient.class);
        java.net.http.HttpResponse<String> response=mock(java.net.http.HttpResponse.class);
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("0");
        when(http.send(any(java.net.http.HttpRequest.class),org.mockito.ArgumentMatchers.<java.net.http.HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        var delivery=new RegistrationDelivery(mock(ObjectProvider.class),false,"","account","00000000000000000000000000000000","短信宝",http);
        delivery.sendSms("13800138000","234567",90);
        var request=org.mockito.ArgumentCaptor.forClass(java.net.http.HttpRequest.class);verify(http).send(request.capture(),any());
        assertThat(request.getValue().uri().getScheme()).isEqualTo("https");
        assertThat(request.getValue().uri().getHost()).isEqualTo("api.smsbao.com");
        assertThat(request.getValue().uri().getPath()).isEqualTo("/sms");
        String query=java.net.URLDecoder.decode(request.getValue().uri().getRawQuery(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(query).contains("m=13800138000","c=【短信宝】您的验证码是234567,90秒内有效");
    }
    @Test void providerFailureShowsUsefulSafeReason() {
        assertThatThrownBy(()->RegistrationDelivery.requireSmsSuccess("41")).hasMessageContaining("余额不足");
        assertThatThrownBy(()->RegistrationDelivery.requireSmsSuccess("30")).hasMessageContaining("账号认证");
    }
    @Test void sendsOnlyToRequestedRecipientAndContainsActualCode() {
        JavaMailSender sender=mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        var delivery=new RegistrationDelivery(provider,true,"sender@example.com","","","短信宝");
        delivery.send("EMAIL","recipient@example.com","234567");
        var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("recipient@example.com");
        assertThat(message.getValue().getText()).contains("234567","5 分钟");
        assertThat(delivery.smsEnabled()).isFalse();
    }
    @Test void disabledDeliveryFailsWithoutAttemptingSend() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        var delivery=new RegistrationDelivery(provider,false,"sender@example.com","","","短信宝");
        assertThatThrownBy(()->delivery.send("EMAIL","user@example.com","234567")).hasMessageContaining("未配置");
        verifyNoInteractions(provider);
    }
    @Test void providerSubmissionFailureIsNotReportedAsSuccess() {
        assertThatCode(()->RegistrationDelivery.requireSmsSuccess("0\n")).doesNotThrowAnyException();
        assertThatThrownBy(()->RegistrationDelivery.requireSmsSuccess("41")).hasMessageContaining("发送失败");
    }
}
