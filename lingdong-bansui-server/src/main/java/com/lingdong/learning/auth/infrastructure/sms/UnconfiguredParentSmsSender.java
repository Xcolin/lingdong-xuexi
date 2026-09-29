package com.lingdong.learning.auth.infrastructure.sms;

import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.ParentSmsSender;
import com.lingdong.learning.auth.application.SmsDeliveryUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

/** 未接入真实短信供应商时的生产适配器，所有发送请求均失败关闭。 */
@Component
@Profile("!local & !test")
@ConditionalOnExpression("!'aliyun'.equalsIgnoreCase('${lingdong.auth.parent-sms.provider:aliyun}')")
public class UnconfiguredParentSmsSender implements ParentSmsSender {
    @Override
    public void send(String mobile, ParentSmsPurpose purpose, String code) {
        throw new SmsDeliveryUnavailableException();
    }
}
