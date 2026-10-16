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
        String email,
        String emailSource,
        UserType type,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        java.util.List<String> organizationNames,
        java.util.List<com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper.RoleIdentity> roleIdentities
) {
    static UserResponse from(User user) {
        return from(user, java.util.List.of());
    }

    static UserResponse from(User user, java.util.List<String> organizationNames) {
        return from(user,organizationNames,java.util.List.of());
    }
    static UserResponse from(User user, java.util.List<String> organizationNames, java.util.List<com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper.RoleIdentity> roleIdentities) {
        return new UserResponse(user.id(), user.username(), user.displayName(), maskMobile(user.mobile()), null, null, user.type(), user.status(),
                user.createdAt(), user.updatedAt(), organizationNames,roleIdentities);
    }

    UserResponse withEmail(com.lingdong.learning.user.application.AdminUserEmailService.Email value) {
        return new UserResponse(id,username,displayName,mobile,value==null?null:value.email(),value==null?null:value.source(),type,status,createdAt,updatedAt,organizationNames,roleIdentities);
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
