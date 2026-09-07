package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 家长账号与单个微信小程序身份的一对一绑定。 */
public record ParentWechatBinding(
        Long id,
        Long userId,
        String appId,
        String openId,
        String unionId,
        ParentWechatBindingStatus status,
        LocalDateTime boundAt,
        LocalDateTime lastLoginAt
) {
}
