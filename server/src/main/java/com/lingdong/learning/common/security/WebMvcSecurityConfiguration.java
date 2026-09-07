package com.lingdong.learning.common.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 将路由权限校验注册到 Spring MVC 的控制器执行链。 */
@Configuration
public class WebMvcSecurityConfiguration implements WebMvcConfigurer {
    private final PermissionAuthorizationInterceptor permissionAuthorizationInterceptor;
    private final ParentOnboardingAccessInterceptor parentOnboardingAccessInterceptor;

    public WebMvcSecurityConfiguration(
            PermissionAuthorizationInterceptor permissionAuthorizationInterceptor,
            ParentOnboardingAccessInterceptor parentOnboardingAccessInterceptor
    ) {
        this.permissionAuthorizationInterceptor = permissionAuthorizationInterceptor;
        this.parentOnboardingAccessInterceptor = parentOnboardingAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(parentOnboardingAccessInterceptor).addPathPatterns("/api/v1/**");
        registry.addInterceptor(permissionAuthorizationInterceptor).addPathPatterns("/api/v1/**");
    }
}
