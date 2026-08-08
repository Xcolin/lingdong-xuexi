package com.lingdong.learning.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 配置无状态 API 认证边界，保留健康检查和登录/刷新接口的公开访问。 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter,
            AuthenticationRequiredEntryPoint authenticationRequiredEntryPoint,
            AccessDeniedResponseHandler accessDeniedResponseHandler
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationRequiredEntryPoint)
                        .accessDeniedHandler(accessDeniedResponseHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/health",
                                "/api/v1/auth/sessions/password",
                                "/api/v1/auth/sessions/refresh",
                                "/api/v1/auth/student-captchas",
                                "/api/v1/auth/student-sessions/code",
                                "/api/v1/auth/student-sessions/qr",
                                "/api/v1/auth/student-qr-captchas",
                                "/api/v1/public/capabilities"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
