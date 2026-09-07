package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.application.ParentSmsSender;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 本地与测试环境的无网络短信适配器，刻意不记录验证码明文。 */
@Component
@Profile({"local", "test"})
public class LocalParentSmsSender implements ParentSmsSender {
    @Override
    public void send(String mobile, ParentSmsPurpose purpose, String code) {
        // 本地和自动化测试不调用供应商，也不输出手机号或验证码。
    }
}
