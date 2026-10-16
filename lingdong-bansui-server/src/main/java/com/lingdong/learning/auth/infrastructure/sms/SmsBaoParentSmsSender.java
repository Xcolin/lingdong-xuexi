package com.lingdong.learning.auth.infrastructure.sms;

import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.infrastructure.config.ParentSmsProperties;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Registration and account security share the same real SMS transport, never the OTP purpose. */
@Component
@ConditionalOnProperty(prefix="lingdong.auth.parent-sms",name="provider",havingValue="smsbao")
public class SmsBaoParentSmsSender implements ParentSmsSender {
    private final RegistrationDelivery delivery;
    private final ParentSmsProperties properties;
    public SmsBaoParentSmsSender(RegistrationDelivery delivery,ParentSmsProperties properties){this.delivery=delivery;this.properties=properties;}
    @Override public void send(String mobile,ParentSmsPurpose purpose,String code){
        try{delivery.sendSms(mobile,code,properties.getCodeTtl().toSeconds());}
        catch(SmsDeliveryUnavailableException e){throw e;}
        catch(IllegalStateException e){throw new SmsDeliveryUnavailableException(e.getMessage());}
    }
}
