package com.lingdong.learning.auth.infrastructure.sms;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SmsBaoParentSmsSenderTest {
    @Test void realLoginDeliveryUsesActualCodeMobileAndConfiguredExpiry() {
        var delivery=mock(RegistrationDelivery.class);
        var properties=new ParentSmsProperties();properties.setCodeTtl(Duration.ofSeconds(90));
        new SmsBaoParentSmsSender(delivery,properties).send("13800138000",ParentSmsPurpose.REGISTER_OR_LOGIN,"234567");
        verify(delivery).sendSms("13800138000","234567",90);
    }
    @Test void providerFailureFailsClosedWithSafeReason() {
        var delivery=mock(RegistrationDelivery.class);
        doThrow(new IllegalStateException("短信发送失败：余额不足")).when(delivery).sendSms(anyString(),anyString(),anyLong());
        assertThatThrownBy(()->new SmsBaoParentSmsSender(delivery,new ParentSmsProperties()).send("13800138000",ParentSmsPurpose.RESET_PASSWORD,"234567"))
            .isInstanceOf(SmsDeliveryUnavailableException.class).hasMessageContaining("余额不足");
    }
}
