package com.lingdong.learning.auth.infrastructure.sms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingdong.learning.auth.application.ParentSmsSender;
import com.lingdong.learning.auth.application.SmsDeliveryUnavailableException;
import com.lingdong.learning.auth.application.ParentSmsPurpose;
import com.lingdong.learning.auth.infrastructure.memory.LocalParentSmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

class ParentSmsSenderSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Senders.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void productionDefaultsToAliyunAndFailsClosedWithoutCredentials() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ParentSmsSender.class);
            assertThat(context.getBean(ParentSmsSender.class)).isInstanceOf(AliyunParentSmsSender.class);
            assertThatThrownBy(() -> context.getBean(ParentSmsSender.class).send(
                    "13800138000", ParentSmsPurpose.REGISTER_OR_LOGIN, "123456"))
                    .isInstanceOf(SmsDeliveryUnavailableException.class);
        });
    }

    @Test
    void unsupportedProviderUsesSingleFailClosedSender() {
        runner.withPropertyValues("lingdong.auth.parent-sms.provider=unsupported").run(context -> {
            assertThat(context).hasSingleBean(ParentSmsSender.class);
            assertThat(context.getBean(ParentSmsSender.class)).isInstanceOf(UnconfiguredParentSmsSender.class);
        });
    }

    @Test
    void localAndTestProfilesRetainOnlyTheirOfflineSender() {
        for (String profile : new String[]{"local", "test"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
                assertThat(context).hasSingleBean(ParentSmsSender.class);
                assertThat(context.getBean(ParentSmsSender.class)).isInstanceOf(LocalParentSmsSender.class);
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AliyunParentSmsProperties.class)
    @Import({AliyunParentSmsSender.class, UnconfiguredParentSmsSender.class, LocalParentSmsSender.class})
    static class Senders { }
}
