package com.lingdong.learning.auth.application;

/** 预认证请求超过受控时间窗口阈值。 */
public class RateLimitedException extends RuntimeException {
    public RateLimitedException() {
        super("请求过于频繁");
    }
}
