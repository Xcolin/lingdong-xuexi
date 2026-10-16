package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.domain.AuthClientType;

import java.util.List;

/** 已登录用户的最小身份摘要。 */
public record CurrentUserResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        @JsonSerialize(using = ToStringSerializer.class) Long sessionId,
        String username,
        String displayName,
        AuthClientType clientType,
        List<String> roleCodes,
        List<String> permissionCodes,
        com.lingdong.learning.user.domain.UserType userType,
        boolean parentProfile,
        boolean teacherProfile,
        List<String> learningTaskSourceTypes
) {
    /** Source compatibility for older tests; no inferred identity from role names. */
    public CurrentUserResponse(Long userId,Long sessionId,String username,String displayName,
            AuthClientType clientType,List<String> roleCodes,List<String> permissionCodes) {
        this(userId,sessionId,username,displayName,clientType,roleCodes,permissionCodes,null,false,false,List.of());
    }
}
