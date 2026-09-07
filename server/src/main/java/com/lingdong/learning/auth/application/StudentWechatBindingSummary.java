package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 家长侧学生微信绑定脱敏摘要。 */
public record StudentWechatBindingSummary(
        Long studentId,
        String studentName,
        String studentAccountMasked,
        boolean bound,
        LocalDateTime boundAt
) {
}
