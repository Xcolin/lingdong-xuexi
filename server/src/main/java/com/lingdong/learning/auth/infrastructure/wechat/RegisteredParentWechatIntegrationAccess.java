package com.lingdong.learning.auth.infrastructure.wechat;

import com.lingdong.learning.auth.application.ParentWechatIntegrationAccess;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;
import com.lingdong.learning.interfaceconfig.infrastructure.persistence.InterfaceServiceMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 生产调用必须已在接口服务管理中登记、启用且微信凭证完整。 */
@Component
@Profile("!test")
public class RegisteredParentWechatIntegrationAccess implements ParentWechatIntegrationAccess {
    private static final String SERVICE_NAME = "微信登录";
    private static final String CALLER_NAME = "miniapp";

    private final InterfaceServiceMapper serviceMapper;
    private final WechatMiniappProperties properties;

    public RegisteredParentWechatIntegrationAccess(
            InterfaceServiceMapper serviceMapper,
            WechatMiniappProperties properties
    ) {
        this.serviceMapper = serviceMapper;
        this.properties = properties;
    }

    @Override
    public void requireAvailable() {
        InterfaceService service = serviceMapper.findByNameAndCaller(SERVICE_NAME, CALLER_NAME);
        if (service == null || service.status() != InterfaceServiceStatus.ENABLED
                || service.direction() != InterfaceDirection.OUTBOUND
                || service.purpose() != InterfacePurpose.WECHAT || !properties.isComplete()) {
            throw new WechatAuthenticationUnavailableException();
        }
    }
}
