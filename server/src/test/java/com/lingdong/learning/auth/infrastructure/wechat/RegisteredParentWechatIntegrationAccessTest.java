package com.lingdong.learning.auth.infrastructure.wechat;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisteredParentWechatIntegrationAccessTest {
    private final InterfaceServiceMapper serviceMapper = mock(InterfaceServiceMapper.class);
    private final WechatMiniappProperties properties = configuredProperties();
    private final RegisteredParentWechatIntegrationAccess access =
            new RegisteredParentWechatIntegrationAccess(serviceMapper, properties);

    @Test
    void requiresEnabledOutboundWechatServiceRegistrationAndCompleteCredentials() {
        assertThatThrownBy(access::requireAvailable)
                .isInstanceOf(WechatAuthenticationUnavailableException.class);

        when(serviceMapper.findByNameAndCaller("微信登录", "miniapp")).thenReturn(new InterfaceService(
                1L, "微信登录", InterfaceDirection.OUTBOUND, InterfacePurpose.WECHAT, "miniapp",
                InterfaceAuthorizationScope.GLOBAL, null, 2L, InterfaceServiceStatus.ENABLED, null, null));

        assertThatCode(access::requireAvailable).doesNotThrowAnyException();
    }

    private WechatMiniappProperties configuredProperties() {
        WechatMiniappProperties value = new WechatMiniappProperties();
        value.setAppId("wx-test-app");
        value.setAppSecret("test-secret");
        return value;
    }
}
