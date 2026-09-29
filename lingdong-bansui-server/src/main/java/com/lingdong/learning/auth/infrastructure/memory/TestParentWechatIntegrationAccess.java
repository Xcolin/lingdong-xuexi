package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentWechatIntegrationAccess;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 测试环境只验证业务契约，不连接或要求远程微信服务登记。 */
@Component
@Profile("test")
public class TestParentWechatIntegrationAccess implements ParentWechatIntegrationAccess {
    @Override
    public void requireAvailable() {
        // 测试假微信网关由测试用例显式控制结果。
    }
}
