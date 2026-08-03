package com.lingdong.learning.common.security;

/**
 * 标识调用方已通过登录及路由权限校验，但未满足系统管理员这一不可委派的业务前置条件。
 *
 * <p>继承 {@link IllegalStateException}，以兼容既有应用服务的异常契约；HTTP 层会将其优先映射为 403。</p>
 */
public class SystemOperationAccessDeniedException extends IllegalStateException {
    public SystemOperationAccessDeniedException(String message) {
        super(message);
    }
}
