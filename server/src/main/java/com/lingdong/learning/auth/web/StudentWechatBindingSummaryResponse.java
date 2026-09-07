package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.application.StudentWechatBindingSummary;

import java.time.LocalDateTime;

/** 家长侧学生微信绑定脱敏响应。 */
public record StudentWechatBindingSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        String studentAccountMasked,
        boolean bound,
        LocalDateTime boundAt
) {
    static StudentWechatBindingSummaryResponse from(StudentWechatBindingSummary summary) {
        return new StudentWechatBindingSummaryResponse(
                summary.studentId(), summary.studentName(), summary.studentAccountMasked(),
                summary.bound(), summary.boundAt());
    }
}
