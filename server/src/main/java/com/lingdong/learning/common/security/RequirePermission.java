package com.lingdong.learning.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明进入控制器方法前必须满足的 RBAC 权限编码。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** 单一必需权限，保留已有控制器的简写用法。 */
    String value() default "";

    /** 至少满足其中一项的权限集合。 */
    String[] anyOf() default {};
}
