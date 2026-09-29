package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;

import java.time.LocalDateTime;

/** 用户管理接口的安全响应，不输出密码散列或会话凭证。 */
public record UserResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String displayName,
        String mobile,
        UserType type,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.displayName(), maskMobile(user.mobile()), user.type(), user.status(),
                user.createdAt(), user.updatedAt());
    }

    private static String maskMobile(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return mobile;
        }
        if (mobile.length() < 7) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
