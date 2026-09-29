package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 学生当前有效微信一对一绑定。 */
public record StudentWechatBinding(
        Long id,
        Long studentId,
        Long studentUserId,
        String appId,
        String openId,
        String unionId,
        LocalDateTime boundAt,
        LocalDateTime lastLoginAt
) {
}
