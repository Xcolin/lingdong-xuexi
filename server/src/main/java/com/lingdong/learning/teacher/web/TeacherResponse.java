package com.lingdong.learning.teacher.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.teacher.application.TeacherAccount;
import com.lingdong.learning.user.domain.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 教师安全响应，不输出密码摘要并统一脱敏手机号。 */
public record TeacherResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String username,
        String displayName,
        String mobile,
        UserStatus status,
        @JsonSerialize(using = ToStringSerializer.class) Long schoolId,
        String schoolName,
        @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> classOrganizationIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static TeacherResponse from(TeacherAccount account) {
        return new TeacherResponse(
                account.id(), account.username(), account.displayName(), maskMobile(account.mobile()),
                account.status(), account.schoolId(), account.schoolName(), account.classOrganizationIds(),
                account.createdAt(), account.updatedAt());
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
