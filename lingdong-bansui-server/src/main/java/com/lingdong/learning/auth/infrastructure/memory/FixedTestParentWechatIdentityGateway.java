package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentWechatIdentity;
import com.lingdong.learning.auth.application.ParentWechatIdentityGateway;
import com.lingdong.learning.auth.infrastructure.wechat.WechatAuthenticationUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 测试环境微信身份网关，保证本地联调和自动化测试不会连接真实微信。 */
@Component
@Profile("test")
public class FixedTestParentWechatIdentityGateway implements ParentWechatIdentityGateway {
    public static final String APP_ID = "wx-test-app";
    public static final String OPEN_ID = "openid-test";
    public static final String UNION_ID = "unionid-test";

    @Override
    public ParentWechatIdentity exchange(String temporaryCode) {
        if (temporaryCode == null || temporaryCode.isBlank()) {
            throw new WechatAuthenticationUnavailableException();
        }
        return new ParentWechatIdentity(APP_ID, OPEN_ID, UNION_ID, "session-key-test");
    }
}
