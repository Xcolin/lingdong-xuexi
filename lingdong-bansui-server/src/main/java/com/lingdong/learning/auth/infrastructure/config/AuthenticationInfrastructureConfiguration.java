package com.lingdong.learning.auth.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/** 提供认证模块所需的受控配置与密码散列器。 */
@Configuration
@EnableConfigurationProperties(AuthenticationProperties.class)
public class AuthenticationInfrastructureConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
